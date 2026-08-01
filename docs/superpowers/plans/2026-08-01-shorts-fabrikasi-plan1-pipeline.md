# Shorts Fabrikası — Plan 1/3: Çekirdek Pipeline

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `generate <channelId>` komutu kanal profilinden, çok dilli render'ları ve `PENDING_REVIEW` durumunda bir `job.json` üreten uçtan uca pipeline'ı kurmak.

**Architecture:** İki fazlı pipeline (Faz 1 dilden bağımsız: senaryo+görseller+müzik; Faz 2 dil başına: çeviri+TTS+altyazı+render). Durum dosya-tabanlı iş klasöründe (`output/jobs/<id>/job.json`, atomik yazım). Tüm API client'ları arayüz arkasında — sahte implementasyonlarla sıfır maliyetli e2e test.

**Tech Stack:** Java SE 17, Maven, Gson, JUnit 5, Mockito, FFmpeg (sistem), OpenAI API (gpt-5.6-luna, gpt-image-2), ElevenLabs (eleven_v3 + timestamps, music_v2).

**Spec:** `docs/superpowers/specs/2026-08-01-shorts-fabrikasi-design.md` (tek doğruluk kaynağı)

**Plan bölümlemesi:** Plan 2/3 = Backoffice (HttpServer + UI). Plan 3/3 = YouTubePublisher + Main/DailyScheduler entegrasyonu + Sora/Suno/ContentGeneratorService silme. Bu plan tamamlanmadan yazılmazlar.

## Global Constraints

- Java 17, **yeni Maven bağımlılığı YOK** (Gson/JUnit5/Mockito/slf4j mevcut)
- Video: **1080×1920**, H.264/AAC, 30 fps; hedef süre **60–90 sn** (profil: `targetDurationSeconds`)
- Model ID'leri (Configuration varsayılanları): `gpt-5.6-luna`, `gpt-image-2` (quality `medium`, size `1024x1536`), `eleven_v3`, `music_v2`
- Görsel promptlarında **yüz yok**; her prompt'a profildeki `stylePrefix` önek olarak eklenir
- Bütçe kontrolü **API çağrılarından ÖNCE** yapılır; aşımda pipeline hiç başlamaz
- `job.json` her adım sonrası **temp+rename** ile atomik yazılır; var olan çıktı dosyası olan adımlar atlanır (resume)
- Commit mesajlarında Claude attribution/Co-Authored-By **YOK**
- Ön koşul: `brew install ffmpeg` (Task 12'den önce kurulu olmalı)
- Test komutu: `mvn -q test -Dtest=<Sınıf>` ; FFmpeg testleri `@Tag("ffmpeg")` ile işaretli, normal koşuda dışlanır (surefire config Task 12'de)

---

### Task 1: Configuration — yeni anahtarlar

**Files:**
- Modify: `src/main/java/com/videogenerator/config/Configuration.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/videogenerator/config/ConfigurationDefaultsTest.java`

**Interfaces:**
- Produces: `getLlmModel()`, `getImageModel()`, `getImageQuality()`, `getImageSize()`, `getTtsModel()`, `getMusicModel()`, `getMonthlyBudgetUsd()`, `getBackofficePort()`, `getChannelsDir()`, `getJobsDir()`, `getCostsDir()` — tümü `String`/`double`/`int` döner; mevcut `getVideoWidth()/getVideoHeight()` 1080/1920 varsayılanına çekilir.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationDefaultsTest {
    @Test
    void newKeysHaveModernDefaults() {
        Configuration c = Configuration.getInstance();
        assertEquals("gpt-5.6-luna", c.getLlmModel());
        assertEquals("gpt-image-2", c.getImageModel());
        assertEquals("medium", c.getImageQuality());
        assertEquals("1024x1536", c.getImageSize());
        assertEquals("eleven_v3", c.getTtsModel());
        assertEquals("music_v2", c.getMusicModel());
        assertEquals(1080, c.getVideoWidth());
        assertEquals(1920, c.getVideoHeight());
        assertTrue(c.getMonthlyBudgetUsd() > 0);
        assertEquals(8080, c.getBackofficePort());
        assertEquals("channels", c.getChannelsDir());
        assertEquals("output/jobs", c.getJobsDir());
        assertEquals("output/costs", c.getCostsDir());
    }
}
```

- [ ] **Step 2: Çalıştır, FAIL doğrula** — `mvn -q test -Dtest=ConfigurationDefaultsTest` → derleme hatası (metod yok)

- [ ] **Step 3: Minimal implementasyon** — `Configuration`'a mevcut getter desenini takip ederek ekle (mevcut `getProperty(key, default)` yardımcıları neyse onu kullan):

```java
public String getLlmModel()        { return getProperty("llm.model", "gpt-5.6-luna"); }
public String getImageModel()      { return getProperty("image.model", "gpt-image-2"); }
public String getImageQuality()    { return getProperty("image.quality", "medium"); }
public String getImageSize()       { return getProperty("image.size", "1024x1536"); }
public String getTtsModel()        { return getProperty("tts.model", "eleven_v3"); }
public String getMusicModel()      { return getProperty("music.model", "music_v2"); }
public double getMonthlyBudgetUsd(){ return Double.parseDouble(getProperty("budget.monthly.usd", "100")); }
public int getBackofficePort()     { return Integer.parseInt(getProperty("backoffice.port", "8080")); }
public String getChannelsDir()     { return getProperty("channels.dir", "channels"); }
public String getJobsDir()         { return getProperty("jobs.dir", "output/jobs"); }
public String getCostsDir()        { return getProperty("costs.dir", "output/costs"); }
```

`application.properties` içinde `video.width=1080`, `video.height=1920`, `video.max.duration=180` olarak güncelle; `tts.model=eleven_v3` yap. Mevcut kodda `getVideoWidth/Height` varsayılanları 720/1280 ise 1080/1920'ye çek.

- [ ] **Step 4: Test PASS doğrula** — `mvn -q test -Dtest=ConfigurationDefaultsTest`
- [ ] **Step 5: Commit** — `git add -u src && git add src/test && git commit -m "feat: add shorts-factory config keys, bump video to 1080x1920"`

---

### Task 2: ChannelProfile + ChannelStore

**Files:**
- Create: `src/main/java/com/videogenerator/channel/ChannelProfile.java`
- Create: `src/main/java/com/videogenerator/channel/NicheSpec.java`
- Create: `src/main/java/com/videogenerator/channel/ChannelStore.java`
- Test: `src/test/java/com/videogenerator/channel/ChannelStoreTest.java`

**Interfaces:**
- Produces: `ChannelProfile` alanları: `String channelId, displayName, stylePrefix, voiceId, youtubeTokenFile; NicheSpec niche; List<String> languages, platforms; int targetDurationSeconds, sceneCount; boolean enabled` + `void validate()` (IllegalArgumentException fırlatır). `NicheSpec`: `String topic; List<String> keywords`. `ChannelStore(Path dir)`: `List<ChannelProfile> loadEnabled()`, `ChannelProfile load(String channelId)`.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.channel;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChannelStoreTest {
    static final String VALID = """
        {"channelId":"truecrime-en","displayName":"Unsolved Files",
         "niche":{"topic":"unsolved crime","keywords":["cold case"]},
         "stylePrefix":"1970s film grain, no human faces",
         "voiceId":"v123","languages":["en","es"],"platforms":["YOUTUBE"],
         "targetDurationSeconds":75,"sceneCount":6,
         "youtubeTokenFile":"config/tokens/truecrime-en.json","enabled":true}""";

    @Test
    void loadsValidProfileAndSkipsDisabled(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("truecrime-en.json"), VALID);
        Files.writeString(dir.resolve("off.json"), VALID.replace("\"enabled\":true", "\"enabled\":false")
                                                        .replace("truecrime-en", "off-ch"));
        ChannelStore store = new ChannelStore(dir);
        List<ChannelProfile> list = store.loadEnabled();
        assertEquals(1, list.size());
        assertEquals("truecrime-en", list.get(0).getChannelId());
        assertEquals(6, list.get(0).getSceneCount());
    }

    @Test
    void validateRejectsMissingVoiceAndBadDuration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("bad.json"),
            VALID.replace("\"voiceId\":\"v123\",", "").replace("75", "30"));
        ChannelStore store = new ChannelStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.load("bad"));
    }
}
```

- [ ] **Step 2: FAIL doğrula** — `mvn -q test -Dtest=ChannelStoreTest`
- [ ] **Step 3: Implementasyon**

```java
// ChannelProfile.java (getter'lar Lombok'suz elle; proje deseni bu)
public class ChannelProfile {
    private String channelId, displayName, stylePrefix, voiceId, youtubeTokenFile;
    private NicheSpec niche;
    private java.util.List<String> languages, platforms;
    private int targetDurationSeconds, sceneCount;
    private boolean enabled;

    public void validate() {
        require(channelId != null && !channelId.isBlank(), "channelId");
        require(voiceId != null && !voiceId.isBlank(), "voiceId");
        require(niche != null && niche.getTopic() != null, "niche.topic");
        require(languages != null && !languages.isEmpty(), "languages");
        require(targetDurationSeconds >= 60 && targetDurationSeconds <= 90,
                "targetDurationSeconds must be 60-90 (TikTok >=60s rule)");
        require(sceneCount >= 3 && sceneCount <= 10, "sceneCount must be 3-10");
    }
    private static void require(boolean ok, String field) {
        if (!ok) throw new IllegalArgumentException("Invalid channel profile: " + field);
    }
    // getters...
}
```

```java
// ChannelStore.java
public class ChannelStore {
    private final Path dir;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    public ChannelStore(Path dir) { this.dir = dir; }

    public ChannelProfile load(String channelId) {
        try {
            ChannelProfile p = gson.fromJson(
                Files.readString(dir.resolve(channelId + ".json")), ChannelProfile.class);
            p.validate();
            return p;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read channel: " + channelId, e);
        }
    }
    public java.util.List<ChannelProfile> loadEnabled() throws java.io.IOException {
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".json"))
                .map(f -> { try { return gson.fromJson(Files.readString(f), ChannelProfile.class); }
                            catch (Exception e) { throw new RuntimeException(f.toString(), e); } })
                .filter(ChannelProfile::isEnabled)
                .peek(ChannelProfile::validate)
                .toList();
        }
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: channel profiles with validation (channels/*.json)"`

---

### Task 3: Job modeli + JobStore (atomik)

**Files:**
- Create: `src/main/java/com/videogenerator/job/Job.java`
- Create: `src/main/java/com/videogenerator/job/JobStatus.java`
- Create: `src/main/java/com/videogenerator/job/CostBreakdown.java`
- Create: `src/main/java/com/videogenerator/model/Story.java`
- Create: `src/main/java/com/videogenerator/model/StoryScene.java`
- Create: `src/main/java/com/videogenerator/model/LangVariant.java`
- Create: `src/main/java/com/videogenerator/model/Publication.java`
- Create: `src/main/java/com/videogenerator/job/JobStore.java`
- Test: `src/test/java/com/videogenerator/job/JobStoreTest.java`

**Interfaces:**
- Produces: `JobStatus` enum: `DRAFTING, RENDERING, PENDING_REVIEW, APPROVED, PUBLISHING, PUBLISHED, FAILED, REJECTED`. `Job`: `String jobId, channelId, error; JobStatus status; Story story; String musicFile; List<LangVariant> variants; CostBreakdown cost` + `static Job create(String channelId)` (id: `yyyy-MM-dd-HHmmss-` + 4 hex). `Story`: `String title, stylePrefix; List<StoryScene> scenes`. `StoryScene`: `int index; String narration, imagePrompt, imageFile`. `LangVariant`: `String lang, audioFile, alignmentFile, renderFile; VideoMetadata metadata; double durationSeconds; List<Publication> publications`. `CostBreakdown`: `double images, tts, music, llm; double total()`. `JobStore(Path root)`: `Path dirFor(String jobId)`, `void save(Job)`, `Job load(String jobId)`, `List<Job> list()`.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JobStoreTest {
    @Test
    void saveIsAtomicAndRoundTrips(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root);
        Job job = Job.create("truecrime-en");
        job.setStatus(JobStatus.RENDERING);
        store.save(job);

        assertFalse(Files.exists(store.dirFor(job.getJobId()).resolve("job.json.tmp")),
                "temp file must be renamed away");
        Job loaded = store.load(job.getJobId());
        assertEquals(JobStatus.RENDERING, loaded.getStatus());
        assertEquals("truecrime-en", loaded.getChannelId());
        assertEquals(1, store.list().size());
    }

    @Test
    void costTotalSums(@TempDir Path root) {
        CostBreakdown c = new CostBreakdown();
        c.setImages(0.48); c.setTts(0.30); c.setMusic(0.22); c.setLlm(0.01);
        assertEquals(1.01, c.total(), 1e-9);
    }
}
```

- [ ] **Step 2: FAIL doğrula** — `mvn -q test -Dtest=JobStoreTest`
- [ ] **Step 3: Implementasyon** — model sınıfları düz POJO (Gson-uyumlu, getter/setter). Kritik parça:

```java
// Job.create
public static Job create(String channelId) {
    Job j = new Job();
    j.jobId = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
              + "-" + Integer.toHexString(new java.util.Random().nextInt(0x10000));
    j.channelId = channelId;
    j.status = JobStatus.DRAFTING;
    j.cost = new CostBreakdown();
    j.variants = new java.util.ArrayList<>();
    return j;
}
```

```java
// JobStore.java
public class JobStore {
    private final Path root;
    private final com.google.gson.Gson gson =
        new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    public JobStore(Path root) { this.root = root; }

    public Path dirFor(String jobId) { return root.resolve(jobId); }

    public synchronized void save(Job job) {
        try {
            Path dir = dirFor(job.getJobId());
            Files.createDirectories(dir);
            Path tmp = dir.resolve("job.json.tmp");
            Files.writeString(tmp, gson.toJson(job));
            Files.move(tmp, dir.resolve("job.json"),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save job " + job.getJobId(), e);
        }
    }
    public Job load(String jobId) {
        try {
            return gson.fromJson(Files.readString(dirFor(jobId).resolve("job.json")), Job.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load job " + jobId, e);
        }
    }
    public java.util.List<Job> list() {
        try (var dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                       .filter(d -> Files.exists(d.resolve("job.json")))
                       .map(d -> load(d.getFileName().toString()))
                       .sorted(java.util.Comparator.comparing(Job::getJobId).reversed())
                       .toList();
        } catch (java.io.IOException e) { return java.util.List.of(); }
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: job model and atomic file-based JobStore"`

---

### Task 4: CostTracker + BudgetGuard

**Files:**
- Create: `src/main/java/com/videogenerator/job/CostTracker.java`
- Create: `src/main/java/com/videogenerator/job/BudgetGuard.java`
- Modify: `src/main/java/com/videogenerator/util/Constants.java`
- Test: `src/test/java/com/videogenerator/job/BudgetGuardTest.java`

**Interfaces:**
- Produces: `CostTracker(Path costsDir)`: `synchronized void add(double usd)` (dosya: `<yyyy-MM>.json`, içerik `{"spent": <double>}`), `double spentThisMonth()`. `BudgetGuard(CostTracker, double monthlyLimit)`: `void assertAllows(double estimatedUsd)` — aşımda `IllegalStateException`. Constants'a: `COST_IMAGE_MEDIUM=0.08`, `COST_TTS_PER_1K_CHARS=0.10`, `COST_MUSIC_TRACK=0.50`, `COST_LLM_CALL=0.01`.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BudgetGuardTest {
    @Test
    void accumulatesAndBlocksOverBudget(@TempDir Path dir) {
        CostTracker tracker = new CostTracker(dir);
        tracker.add(40.0);
        tracker.add(20.0);
        assertEquals(60.0, tracker.spentThisMonth(), 1e-9);

        BudgetGuard guard = new BudgetGuard(tracker, 100.0);
        assertDoesNotThrow(() -> guard.assertAllows(39.0));
        assertThrows(IllegalStateException.class, () -> guard.assertAllows(41.0));
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        new CostTracker(dir).add(5.0);
        assertEquals(5.0, new CostTracker(dir).spentThisMonth(), 1e-9);
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon**

```java
public class CostTracker {
    private final Path dir;
    public CostTracker(Path dir) { this.dir = dir; }
    private Path file() {
        String ym = java.time.YearMonth.now().toString(); // "2026-08"
        return dir.resolve(ym + ".json");
    }
    public synchronized void add(double usd) {
        try {
            java.nio.file.Files.createDirectories(dir);
            double next = spentThisMonth() + usd;
            Path tmp = dir.resolve(file().getFileName() + ".tmp");
            java.nio.file.Files.writeString(tmp, "{\"spent\": " + next + "}");
            java.nio.file.Files.move(tmp, file(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    public double spentThisMonth() {
        try {
            if (!java.nio.file.Files.exists(file())) return 0.0;
            var o = new com.google.gson.Gson().fromJson(
                java.nio.file.Files.readString(file()), com.google.gson.JsonObject.class);
            return o.get("spent").getAsDouble();
        } catch (java.io.IOException e) { return 0.0; }
    }
}

public class BudgetGuard {
    private final CostTracker tracker; private final double limit;
    public BudgetGuard(CostTracker t, double limit) { this.tracker = t; this.limit = limit; }
    public void assertAllows(double estimatedUsd) {
        double spent = tracker.spentThisMonth();
        if (spent + estimatedUsd > limit)
            throw new IllegalStateException(String.format(
                "Budget exceeded: spent=%.2f + estimate=%.2f > limit=%.2f", spent, estimatedUsd, limit));
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: monthly cost tracking with pre-call budget guard"`

---

### Task 5: LlmClient arayüzü + StoryWriter

**Files:**
- Create: `src/main/java/com/videogenerator/api/LlmClient.java`
- Modify: `src/main/java/com/videogenerator/api/OpenAiGptClient.java`
- Create: `src/main/java/com/videogenerator/service/StoryWriter.java`
- Test: `src/test/java/com/videogenerator/service/StoryWriterTest.java`

**Interfaces:**
- Produces: `interface LlmClient { String complete(String systemPrompt, String userPrompt) throws ApiException; }`. `OpenAiGptClient implements LlmClient` — `DEFAULT_MODEL` sabiti silinir, model `config.getLlmModel()`'den okunur; `complete()` mevcut chat çağrı yolunu kullanır (sıcaklık 0.8, `response_format {"type":"json_object"}` YOK — düz metin; JSON'u prompt zorlar). `StoryWriter(LlmClient llm)`: `Story write(ContentIdea idea, ChannelProfile profile) throws ApiException` — sahne sayısı `profile.getSceneCount()`, anlatı İngilizce, her `imagePrompt` başına `stylePrefix` **eklenmez** (SceneImageService ekler; çift eklemeyi önlemek için tek sorumlu o).

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoryWriterTest {
    static final String LLM_JSON = """
        {"title":"The Lighthouse Keeper Who Vanished",
         "scenes":[
           {"narration":"In 1972, a keeper disappeared.","imagePrompt":"abandoned lighthouse at dusk"},
           {"narration":"His logbook ended mid-sentence.","imagePrompt":"open logbook on wooden desk"},
           {"narration":"The door was locked from inside.","imagePrompt":"rusted iron door bolt close-up"}
         ]}""";

    @Test
    void parsesScenesAndAssignsIndexes() throws Exception {
        LlmClient fake = (sys, user) -> LLM_JSON;
        ChannelProfile p = TestProfiles.withSceneCount(3); // helper aşağıda
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished keeper");

        Story story = new StoryWriter(fake).write(idea, p);

        assertEquals(3, story.getScenes().size());
        assertEquals(1, story.getScenes().get(0).getIndex());
        assertEquals("open logbook on wooden desk", story.getScenes().get(1).getImagePrompt());
        assertEquals(p.getStylePrefix(), story.getStylePrefix());
    }

    @Test
    void rejectsWrongSceneCount() {
        LlmClient fake = (sys, user) -> LLM_JSON; // 3 sahne döner
        ChannelProfile p = TestProfiles.withSceneCount(6);
        assertThrows(IllegalStateException.class,
            () -> new StoryWriter(fake).write(new ContentIdea(), p));
    }
}
```

`TestProfiles` yardımcı sınıfı (`src/test/java/com/videogenerator/channel/TestProfiles.java`): Gson ile Task 2'deki `VALID` JSON'unu parse edip `sceneCount`'u değiştirerek döner — public static `ChannelProfile withSceneCount(int n)`.

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon**

```java
// LlmClient.java
public interface LlmClient {
    String complete(String systemPrompt, String userPrompt) throws com.videogenerator.util.ApiException;
}
```

`OpenAiGptClient`: sınıf imzasına `implements LlmClient` ekle; `complete(sys, user)` mevcut private chat-çağrı yardımcının üstüne kur (model: `config.getLlmModel()`); `DEFAULT_MODEL` sabitini ve `"gpt-4"` geçen her yeri kaldır.

```java
// StoryWriter.java
public class StoryWriter {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StoryWriter.class);
    private final LlmClient llm;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    public StoryWriter(LlmClient llm) { this.llm = llm; }

    public Story write(ContentIdea idea, ChannelProfile profile) throws ApiException {
        String system = "You write scripts for short vertical documentary videos. "
            + "Respond with ONLY valid JSON, no markdown fences.";
        String user = String.format("""
            Topic: %s
            Niche: %s
            Write a gripping %d-second story in English split into EXACTLY %d scenes.
            Each scene: 1-2 spoken sentences ("narration") and one visual description
            ("imagePrompt") showing PLACES, OBJECTS, DOCUMENTS or SILHOUETTES - never a
            recognizable human face. JSON shape:
            {"title": "...", "scenes":[{"narration":"...","imagePrompt":"..."}]}""",
            idea.getTitle(), profile.getNiche().getTopic(),
            profile.getTargetDurationSeconds(), profile.getSceneCount());

        String raw = llm.complete(system, user).trim();
        if (raw.startsWith("```")) raw = raw.replaceAll("^```(json)?\\s*|\\s*```$", "");
        Story story = gson.fromJson(raw, Story.class);
        if (story.getScenes() == null || story.getScenes().size() != profile.getSceneCount())
            throw new IllegalStateException("LLM returned " +
                (story.getScenes() == null ? 0 : story.getScenes().size()) +
                " scenes, expected " + profile.getSceneCount());
        for (int i = 0; i < story.getScenes().size(); i++)
            story.getScenes().get(i).setIndex(i + 1);
        story.setStylePrefix(profile.getStylePrefix());
        logger.info("Story written: {} ({} scenes)", story.getTitle(), story.getScenes().size());
        return story;
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: StoryWriter generates N-scene story via LlmClient interface"`

---

### Task 6: TranslationService

**Files:**
- Create: `src/main/java/com/videogenerator/service/TranslationService.java`
- Create: `src/main/java/com/videogenerator/model/LocalizedStory.java`
- Test: `src/test/java/com/videogenerator/service/TranslationServiceTest.java`

**Interfaces:**
- Produces: `LocalizedStory`: `List<String> narrations; VideoMetadata metadata` (metadata: mevcut `VideoMetadata` — title/description/hashtags). `TranslationService(LlmClient llm)`: `LocalizedStory localize(Story story, String lang) throws ApiException`. `lang="en"` dahil her dil LLM'den geçer (en için "localize" değil "metadata üret" işlevi görür — tek kod yolu).

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TranslationServiceTest {
    static final String TR_JSON = """
        {"narrations":["1972'de bir bekçi kayboldu.","Seyir defteri yarım kaldı."],
         "metadata":{"title":"Kaybolan Fener Bekçisi","description":"Gerçek bir gizem.",
                     "hashtags":["#gizem","#gerçeksuç"]}}""";

    @Test
    void localizesNarrationsAndMetadata() throws Exception {
        Story story = new Story();
        story.setTitle("The Lighthouse Keeper Who Vanished");
        StoryScene s1 = new StoryScene(); s1.setNarration("In 1972, a keeper disappeared.");
        StoryScene s2 = new StoryScene(); s2.setNarration("His logbook ended mid-sentence.");
        story.setScenes(List.of(s1, s2));

        LlmClient fake = (sys, user) -> TR_JSON;
        LocalizedStory loc = new TranslationService(fake).localize(story, "tr");

        assertEquals(2, loc.getNarrations().size());
        assertEquals("Kaybolan Fener Bekçisi", loc.getMetadata().getTitle());
    }

    @Test
    void rejectsNarrationCountMismatch() {
        Story story = new Story();
        StoryScene s1 = new StoryScene(); s1.setNarration("one");
        story.setScenes(List.of(s1)); // 1 sahne, fake 2 döner
        LlmClient fake = (sys, user) -> TR_JSON;
        assertThrows(IllegalStateException.class,
            () -> new TranslationService(fake).localize(story, "tr"));
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon** — prompt: sahne anlatılarını numaralı liste olarak ver, hedef dile çevir + o dilde viral metadata iste; JSON şekli test fixture'ı ile aynı. Sahne sayısı doğrulaması `IllegalStateException`. Markdown fence temizliği StoryWriter'daki ile aynı (kopyala — iki sınıf, DRY için `LlmJson.strip(String)` küçük util'i `api` paketine koy ve ikisinde de kullan).
- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: TranslationService localizes narrations and metadata per language"`

---

### Task 7: ImageApiClient + SceneImageService

**Files:**
- Create: `src/main/java/com/videogenerator/api/ImageGenerator.java`
- Create: `src/main/java/com/videogenerator/api/ImageApiClient.java`
- Create: `src/main/java/com/videogenerator/service/SceneImageService.java`
- Test: `src/test/java/com/videogenerator/api/ImageApiClientTest.java`
- Test: `src/test/java/com/videogenerator/service/SceneImageServiceTest.java`

**Interfaces:**
- Produces: `interface ImageGenerator { java.io.File generate(String prompt, java.nio.file.Path outFile) throws ApiException; }`. `ImageApiClient implements ImageGenerator` — `POST {OPENAI_API_BASE_URL}/images/generations` body `{model, prompt, size, quality, n:1}`, yanıt `data[0].b64_json` → decode → PNG yaz. `static String buildRequestBody(String model, String prompt, String size, String quality)` (test için). `SceneImageService(ImageGenerator gen)`: `double generateAll(Story story, Path scenesDir)` — her sahne için `stylePrefix + ", " + imagePrompt` ile üretir, dosya adı `%02d.png`, **var olan dosyayı atlar** (resume), `StoryScene.imageFile`'ı `scenes/NN.png` olarak set eder, üretilen görsel başına `Constants.COST_IMAGE_MEDIUM` toplayıp döner.

- [ ] **Step 1: Failing testleri yaz**

```java
// ImageApiClientTest.java
package com.videogenerator.api;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageApiClientTest {
    @Test
    void buildsCorrectRequestBody() {
        String body = ImageApiClient.buildRequestBody(
            "gpt-image-2", "old lighthouse", "1024x1536", "medium");
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("gpt-image-2", o.get("model").getAsString());
        assertEquals("1024x1536", o.get("size").getAsString());
        assertEquals("medium", o.get("quality").getAsString());
        assertEquals(1, o.get("n").getAsInt());
    }
}
```

```java
// SceneImageServiceTest.java
package com.videogenerator.service;

import com.videogenerator.api.ImageGenerator;
import com.videogenerator.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SceneImageServiceTest {
    private Story storyWith(int n) {
        Story s = new Story(); s.setStylePrefix("film grain, no faces");
        List<StoryScene> scenes = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            StoryScene sc = new StoryScene();
            sc.setIndex(i); sc.setImagePrompt("prompt " + i);
            scenes.add(sc);
        }
        s.setScenes(scenes); return s;
    }

    @Test
    void prependsStyleSkipsExistingAndTracksCost(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("01.png"), "already-here"); // sahne 1 mevcut
        List<String> prompts = new ArrayList<>();
        ImageGenerator fake = (prompt, out) -> {
            prompts.add(prompt);
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        Story story = storyWith(3);
        double cost = new SceneImageService(fake).generateAll(story, dir);

        assertEquals(2, prompts.size()); // 1 atlandı
        assertTrue(prompts.get(0).startsWith("film grain, no faces, "));
        assertEquals("scenes/02.png", story.getScenes().get(1).getImageFile());
        assertEquals(2 * com.videogenerator.util.Constants.COST_IMAGE_MEDIUM, cost, 1e-9);
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon** — `ImageApiClient` mevcut `HttpUtil.post` + `RetryPolicy` desenini `ElevenLabsClient` ile aynı şekilde kullanır; `Authorization: Bearer` başlığı `config.getOpenAiApiKey()`. `SceneImageService.generateAll` sahne döngüsü:

```java
public double generateAll(Story story, Path scenesDir) throws ApiException, java.io.IOException {
    java.nio.file.Files.createDirectories(scenesDir);
    double cost = 0;
    for (StoryScene scene : story.getScenes()) {
        String name = String.format("%02d.png", scene.getIndex());
        Path out = scenesDir.resolve(name);
        scene.setImageFile("scenes/" + name);
        if (java.nio.file.Files.exists(out)) { logger.info("Skip existing {}", name); continue; }
        gen.generate(story.getStylePrefix() + ", " + scene.getImagePrompt(), out);
        cost += Constants.COST_IMAGE_MEDIUM;
    }
    return cost;
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: gpt-image-2 client + scene image service with style lock and resume"`

---

### Task 8: ElevenLabs timestamps + Alignment

**Files:**
- Create: `src/main/java/com/videogenerator/model/Alignment.java`
- Modify: `src/main/java/com/videogenerator/api/ElevenLabsClient.java`
- Create: `src/test/resources/fixtures/alignment-sample.json`
- Test: `src/test/java/com/videogenerator/api/AlignmentParseTest.java`

**Interfaces:**
- Produces: `Alignment`: `List<String> characters; List<Double> characterStartTimesSeconds, characterEndTimesSeconds` (`@SerializedName` ile snake_case eşleme) + `int length()`, `double endOf(int charIndex)`, `double totalDuration()`. `ElevenLabsClient`'a: `TtsResult generateWithTimestamps(String text, VoiceConfig cfg, Path audioOut, Path alignmentOut) throws ApiException` — endpoint `POST /v1/text-to-speech/{voiceId}/with-timestamps`, body mevcut TTS body + `model_id`; yanıttan `audio_base64` decode → mp3 yaz, `alignment` objesini ham JSON olarak `alignmentOut`'a yaz. `record TtsResult(java.io.File audioFile, Alignment alignment)`. `static Alignment parseAlignment(String responseJson)` (test için).

- [ ] **Step 1: Fixture + failing test yaz**

`alignment-sample.json` (gerçek yanıt şekli):
```json
{"audio_base64":"//uQxAAA",
 "alignment":{
   "characters":["H","i","."," ","B","y","e","."],
   "character_start_times_seconds":[0.0,0.08,0.16,0.24,0.30,0.42,0.55,0.68],
   "character_end_times_seconds":[0.08,0.16,0.24,0.30,0.42,0.55,0.68,0.80]}}
```

```java
package com.videogenerator.api;

import com.videogenerator.model.Alignment;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AlignmentParseTest {
    @Test
    void parsesSnakeCaseFieldsAndComputesDuration() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/alignment-sample.json"));
        Alignment a = ElevenLabsClient.parseAlignment(json);
        assertEquals(8, a.length());
        assertEquals(0.30, a.endOf(3), 1e-9);
        assertEquals(0.80, a.totalDuration(), 1e-9);
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon** — `parseAlignment`: Gson ile dış objeyi parse et, `alignment` alanını `Alignment.class`'a çevir. `generateWithTimestamps`: mevcut `generateVoiceover`'ın request gövdesini yeniden kullan (`model_id: config.getTtsModel()`), URL'e `/with-timestamps` ekle, yanıtı parse et, `Base64.getDecoder()` ile mp3 yaz, alignment'ı pretty JSON olarak kaydet. `totalDuration()` = son karakterin end değeri (boş listede 0).
- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: ElevenLabs with-timestamps endpoint + Alignment model"`

---

### Task 9: SceneTimer — sahne kesim noktaları

**Files:**
- Create: `src/main/java/com/videogenerator/processor/SceneTimer.java`
- Test: `src/test/java/com/videogenerator/processor/SceneTimerTest.java`

**Interfaces:**
- Produces: `static String joinNarrations(List<String>)` → `String.join(" ", ...)` — **TTS'e giden metin ve kesim hesabı AYNI fonksiyonu kullanmak zorunda** (senkronun temeli). `static double[] sceneEndTimes(List<String> narrations, Alignment a)` → her sahnenin bitiş saniyesi; son sahnenin bitişi = `a.totalDuration()`. `static double[] sceneDurations(double[] endTimes)` → ardışık farklar.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.processor;

import com.videogenerator.model.Alignment;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneTimerTest {
    // "Hi. Bye." → sahneler: ["Hi.", "Bye."], birleşik: "Hi. Bye." (8 karakter)
    private Alignment fixture() {
        Alignment a = new Alignment();
        a.setCharacters(List.of("H","i","."," ","B","y","e","."));
        a.setCharacterStartTimesSeconds(List.of(0.0,0.08,0.16,0.24,0.30,0.42,0.55,0.68));
        a.setCharacterEndTimesSeconds(List.of(0.08,0.16,0.24,0.30,0.42,0.55,0.68,0.80));
        return a;
    }

    @Test
    void cutsAtLastCharOfEachScene() {
        double[] ends = SceneTimer.sceneEndTimes(List.of("Hi.", "Bye."), fixture());
        assertArrayEquals(new double[]{0.24, 0.80}, ends, 1e-9);
        assertArrayEquals(new double[]{0.24, 0.56}, SceneTimer.sceneDurations(ends), 1e-9);
    }

    @Test
    void singleSceneEndsAtTotal() {
        double[] ends = SceneTimer.sceneEndTimes(List.of("Hi. Bye."), fixture());
        assertArrayEquals(new double[]{0.80}, ends, 1e-9);
    }

    @Test
    void joinMatchesCharacterCount() {
        String joined = SceneTimer.joinNarrations(List.of("Hi.", "Bye."));
        assertEquals(fixture().length(), joined.length());
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon**

```java
public final class SceneTimer {
    private SceneTimer() {}

    public static String joinNarrations(java.util.List<String> narrations) {
        return String.join(" ", narrations);
    }

    /** Sahne N'in bitişi = birleşik metindeki son karakterinin end zamanı. */
    public static double[] sceneEndTimes(java.util.List<String> narrations,
                                         com.videogenerator.model.Alignment a) {
        double[] ends = new double[narrations.size()];
        int cursor = -1; // birleşik metinde son işlenen karakter indeksi
        for (int i = 0; i < narrations.size(); i++) {
            cursor += narrations.get(i).length();
            if (i > 0) cursor += 1; // aradaki boşluk
            int idx = Math.min(cursor, a.length() - 1); // normalizasyon farkına tolerans
            ends[i] = a.endOf(idx);
        }
        ends[narrations.size() - 1] = a.totalDuration(); // son sahne daima sona uzar
        return ends;
    }

    public static double[] sceneDurations(double[] endTimes) {
        double[] d = new double[endTimes.length];
        double prev = 0;
        for (int i = 0; i < endTimes.length; i++) { d[i] = endTimes[i] - prev; prev = endTimes[i]; }
        return d;
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: SceneTimer derives scene cut points from TTS alignment"`

---

### Task 10: SubtitleRenderer — ASS üretimi

**Files:**
- Create: `src/main/java/com/videogenerator/processor/SubtitleRenderer.java`
- Create: `src/main/java/com/videogenerator/model/SubtitleCue.java`
- Test: `src/test/java/com/videogenerator/processor/SubtitleRendererTest.java`

**Interfaces:**
- Produces: `SubtitleCue`: `double start, end; String text`. `SubtitleRenderer`: `static List<SubtitleCue> buildCues(Alignment a, int maxWordsPerCue)` (kelimeler boşluk karakterlerinden bölünür; cue = ardışık `maxWordsPerCue` kelime; cue.start = ilk kelimenin ilk karakterinin start'ı, cue.end = son kelimenin son karakterinin end'i), `static String toAss(List<SubtitleCue> cues)` (PlayRes 1080×1920, alt güvenli bölge: `MarginV=420` — spec'teki 390px alt marj + pay), `static java.io.File write(List<SubtitleCue> cues, Path out)`.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.processor;

import com.videogenerator.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubtitleRendererTest {
    private Alignment fixture() { // "Hi. Bye." — SceneTimerTest ile aynı
        Alignment a = new Alignment();
        a.setCharacters(List.of("H","i","."," ","B","y","e","."));
        a.setCharacterStartTimesSeconds(List.of(0.0,0.08,0.16,0.24,0.30,0.42,0.55,0.68));
        a.setCharacterEndTimesSeconds(List.of(0.08,0.16,0.24,0.30,0.42,0.55,0.68,0.80));
        return a;
    }

    @Test
    void groupsWordsIntoCues() {
        List<SubtitleCue> cues = SubtitleRenderer.buildCues(fixture(), 1);
        assertEquals(2, cues.size());
        assertEquals("Hi.", cues.get(0).getText());
        assertEquals(0.0, cues.get(0).getStart(), 1e-9);
        assertEquals(0.24, cues.get(0).getEnd(), 1e-9);
        assertEquals("Bye.", cues.get(1).getText());
        assertEquals(0.30, cues.get(1).getStart(), 1e-9);
    }

    @Test
    void assContainsHeaderAndDialogue() {
        String ass = SubtitleRenderer.toAss(SubtitleRenderer.buildCues(fixture(), 2));
        assertTrue(ass.contains("PlayResX: 1080"));
        assertTrue(ass.contains("PlayResY: 1920"));
        assertTrue(ass.contains("Dialogue: 0,0:00:00.00,0:00:00.80,Default"));
        assertTrue(ass.contains("Hi. Bye."));
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon**

```java
public final class SubtitleRenderer {
    private SubtitleRenderer() {}

    public static java.util.List<SubtitleCue> buildCues(Alignment a, int maxWordsPerCue) {
        record Word(int startIdx, int endIdx) {}
        java.util.List<Word> words = new java.util.ArrayList<>();
        int wordStart = -1;
        for (int i = 0; i < a.length(); i++) {
            boolean space = a.getCharacters().get(i).isBlank();
            if (!space && wordStart < 0) wordStart = i;
            if ((space || i == a.length() - 1) && wordStart >= 0) {
                words.add(new Word(wordStart, space ? i - 1 : i));
                wordStart = -1;
            }
        }
        java.util.List<SubtitleCue> cues = new java.util.ArrayList<>();
        for (int w = 0; w < words.size(); w += maxWordsPerCue) {
            int last = Math.min(w + maxWordsPerCue, words.size()) - 1;
            StringBuilder text = new StringBuilder();
            for (int k = w; k <= last; k++) {
                if (k > w) text.append(' ');
                for (int c = words.get(k).startIdx(); c <= words.get(k).endIdx(); c++)
                    text.append(a.getCharacters().get(c));
            }
            SubtitleCue cue = new SubtitleCue();
            cue.setStart(a.getCharacterStartTimesSeconds().get(words.get(w).startIdx()));
            cue.setEnd(a.getCharacterEndTimesSeconds().get(words.get(last).endIdx()));
            cue.setText(text.toString());
            cues.add(cue);
        }
        return cues;
    }

    public static String toAss(java.util.List<SubtitleCue> cues) {
        StringBuilder sb = new StringBuilder("""
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1080
            PlayResY: 1920

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Outline, Shadow, Alignment, MarginL, MarginR, MarginV
            Style: Default,Arial,72,&H00FFFFFF,&H00000000,&H80000000,-1,4,0,2,60,60,420

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            """);
        for (SubtitleCue c : cues)
            sb.append(String.format("Dialogue: 0,%s,%s,Default,,0,0,0,,%s%n",
                assTime(c.getStart()), assTime(c.getEnd()), c.getText()));
        return sb.toString();
    }

    static String assTime(double s) {
        int h = (int) (s / 3600), m = (int) ((s % 3600) / 60);
        double sec = s % 60;
        return String.format(java.util.Locale.ROOT, "%d:%02d:%05.2f", h, m, sec);
    }

    public static java.io.File write(java.util.List<SubtitleCue> cues, java.nio.file.Path out)
            throws java.io.IOException {
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, toAss(cues));
        return out.toFile();
    }
}
```

- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: burn-in subtitle renderer (alignment -> word cues -> ASS)"`

---

### Task 11: MusicApiClient

**Files:**
- Create: `src/main/java/com/videogenerator/api/MusicGenerator.java`
- Create: `src/main/java/com/videogenerator/api/MusicApiClient.java`
- Test: `src/test/java/com/videogenerator/api/MusicApiClientTest.java`

**Interfaces:**
- Produces: `interface MusicGenerator { java.io.File generate(String prompt, int durationSeconds, java.nio.file.Path out) throws ApiException; }`. `MusicApiClient implements MusicGenerator` — `POST https://api.elevenlabs.io/v1/music` body `{prompt, music_length_ms, model_id}`, header `xi-api-key`, yanıt ham audio bytes → mp3 yaz. `static String buildRequestBody(String prompt, int durationSeconds, String modelId)`.

- [ ] **Step 1: Failing test yaz**

```java
package com.videogenerator.api;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicApiClientTest {
    @Test
    void buildsBodyWithMillisecondsAndModel() {
        String body = MusicApiClient.buildRequestBody("tense ambient", 75, "music_v2");
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("tense ambient", o.get("prompt").getAsString());
        assertEquals(75000, o.get("music_length_ms").getAsInt());
        assertEquals("music_v2", o.get("model_id").getAsString());
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon** — `ElevenLabsClient`'taki `xi-api-key` header ve `HttpUtil` byte-yanıt desenini kullan (mevcut `HttpUtil`'de byte[] indirme yoksa `HttpUtil.postForBytes(url, body, headers)` ekle — `HttpResponse.BodyHandlers.ofByteArray()`).
- [ ] **Step 4: PASS doğrula**
- [ ] **Step 5: Commit** — `git commit -m "feat: ElevenLabs music_v2 client"`

---

### Task 12: KenBurnsRenderer

**Files:**
- Create: `src/main/java/com/videogenerator/processor/KenBurnsRenderer.java`
- Modify: `src/main/java/com/videogenerator/processor/FFmpegWrapper.java` (`executeCommand`'ı `public` yap; stderr'i exception mesajına ekle)
- Modify: `pom.xml` (surefire: `@Tag("ffmpeg")` testlerini varsayılan koşudan dışla — `<excludedGroups>ffmpeg</excludedGroups>`)
- Test: `src/test/java/com/videogenerator/processor/KenBurnsFilterTest.java` (saf, hızlı)
- Test: `src/test/java/com/videogenerator/processor/KenBurnsRenderIT.java` (`@Tag("ffmpeg")`)

**Interfaces:**
- Produces: `KenBurnsRenderer(FFmpegWrapper ffmpeg)`: `java.io.File render(java.util.List<java.io.File> sceneImages, double[] sceneDurations, java.io.File mixedAudio, java.io.File assFile, java.nio.file.Path out)`. `static String buildFilterGraph(double[] durations, String assPath, int fps)` — sahne başına `scale+crop+zoompan` (çift indeks zoom-in `z='1+0.10*on/D'`, tek indeks zoom-out `z='1.10-0.10*on/D'`, `D=round(dur*fps)`), sahneler arası `xfade=transition=fade:duration=0.5`, sona `subtitles='<assPath>'`, çıkış etiketi `[vout]`.

- [ ] **Step 1: Failing filter testi yaz**

```java
package com.videogenerator.processor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KenBurnsFilterTest {
    @Test
    void twoScenesProduceZoompanXfadeAndSubtitles() {
        String g = KenBurnsRenderer.buildFilterGraph(new double[]{4.0, 3.0}, "subs/en.ass", 30);
        assertTrue(g.contains("zoompan=z='1+0.10*on/120'"));   // sahne 0: 4.0s*30fps, zoom-in
        assertTrue(g.contains("zoompan=z='1.10-0.10*on/90'")); // sahne 1: zoom-out
        assertTrue(g.contains("xfade=transition=fade:duration=0.5:offset=3.5")); // 4.0-0.5
        assertTrue(g.contains("subtitles='subs/en.ass'"));
        assertTrue(g.endsWith("[vout]"));
    }

    @Test
    void singleSceneHasNoXfade() {
        String g = KenBurnsRenderer.buildFilterGraph(new double[]{5.0}, "s.ass", 30);
        assertFalse(g.contains("xfade"));
        assertTrue(g.contains("subtitles='s.ass'"));
    }
}
```

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon**

```java
public class KenBurnsRenderer {
    private final FFmpegWrapper ffmpeg;
    public KenBurnsRenderer(FFmpegWrapper ffmpeg) { this.ffmpeg = ffmpeg; }

    static String buildFilterGraph(double[] durations, String assPath, int fps) {
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < durations.length; i++) {
            long frames = Math.round(durations[i] * fps);
            String zoom = (i % 2 == 0)
                ? "1+0.10*on/" + frames
                : "1.10-0.10*on/" + frames;
            g.append(String.format(java.util.Locale.ROOT,
                "[%d:v]scale=1080:1920:force_original_aspect_ratio=increase," +
                "crop=1080:1920,zoompan=z='%s':d=%d:s=1080x1920:fps=%d[v%d];",
                i, zoom, frames, fps, i));
        }
        String prev = "[v0]";
        double offset = 0;
        for (int i = 1; i < durations.length; i++) {
            offset += durations[i - 1] - 0.5;
            String outLabel = "[x" + i + "]";
            g.append(String.format(java.util.Locale.ROOT,
                "%s[v%d]xfade=transition=fade:duration=0.5:offset=%.1f%s;",
                prev, i, offset, outLabel));
            prev = outLabel;
        }
        g.append(prev).append("subtitles='").append(assPath).append("'[vout]");
        return g.toString();
    }

    public java.io.File render(java.util.List<java.io.File> images, double[] durations,
                               java.io.File mixedAudio, java.io.File assFile,
                               java.nio.file.Path out) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("ffmpeg"));
        for (int i = 0; i < images.size(); i++) {
            cmd.addAll(java.util.List.of("-loop", "1",
                "-t", String.valueOf(durations[i] + 0.5),
                "-i", images.get(i).getAbsolutePath()));
        }
        cmd.addAll(java.util.List.of("-i", mixedAudio.getAbsolutePath(),
            "-filter_complex", buildFilterGraph(durations, assFile.getPath(), 30),
            "-map", "[vout]", "-map", images.size() + ":a",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
            "-shortest", "-y", out.toString()));
        ffmpeg.executeCommand(cmd, "ken burns render");
        return out.toFile();
    }
}
```

`FFmpegWrapper.executeCommand`'ı public yaparken: process exit != 0 ise stderr'in son 20 satırını `VideoProcessingException` mesajına ekle (spec §7 — FFmpeg hataları yüzeye çıkmalı).

- [ ] **Step 4: Filter testi PASS doğrula** — `mvn -q test -Dtest=KenBurnsFilterTest`
- [ ] **Step 5: Entegrasyon testi yaz (`@Tag("ffmpeg")`)** — `KenBurnsRenderIT`: 2 tek renk PNG üret (`java.awt.image.BufferedImage` + `ImageIO.write`), 2 sn sessiz ses üret (`ffmpeg -f lavfi -i anullsrc -t 2 silent.mp3` — `ffmpeg.executeCommand` ile), minimal ASS yaz, `render(...)` çağır, çıktı mp4'ün var ve >0 byte olduğunu ve `FFmpegWrapper.getMediaDuration` ile süresinin ~3.5±0.5 sn olduğunu doğrula.
- [ ] **Step 6: FFmpeg testini koş** — `mvn -q test -Dtest=KenBurnsRenderIT -Dgroups=ffmpeg` → PASS (FFmpeg kuruluysa; değilse önce `brew install ffmpeg`)
- [ ] **Step 7: Commit** — `git commit -m "feat: Ken Burns renderer (zoompan+xfade+subtitles filter graph)"`

---

### Task 13: JobPipeline — orkestrasyon

**Files:**
- Create: `src/main/java/com/videogenerator/job/JobPipeline.java`
- Test: `src/test/java/com/videogenerator/job/JobPipelineTest.java`

**Interfaces:**
- Consumes: Task 2-12'nin tüm arayüzleri.
- Produces: `JobPipeline` constructor: `(JobStore, ChannelStore, LlmClient, ImageGenerator, MusicGenerator, TtsEngine, RenderEngine, BudgetGuard, CostTracker, IdeaGenerator)`. Burada `TtsEngine` ve `RenderEngine` bu task'ta tanımlanan iki küçük arayüz: `interface TtsEngine { ElevenLabsClient.TtsResult speak(String text, String voiceId, Path audioOut, Path alignOut) throws ApiException; }` (ElevenLabsClient'a adapter); `interface RenderEngine { java.io.File render(List<java.io.File> images, double[] durations, java.io.File voiceover, java.io.File music, java.io.File ass, Path out) throws Exception; }` — gerçek implementasyon `DefaultRenderEngine` (bu task'ta): önce `AudioProcessor.mixVoiceoverAndMusic`, sonra `KenBurnsRenderer.render`. `run(String channelId)` → `Job` (durum `PENDING_REVIEW`); hata halinde durum `FAILED` + `error` alanı dolu, exception yukarı fırlatılır.

- [ ] **Step 1: Failing e2e test yaz (tüm bağımlılıklar sahte)**

```java
package com.videogenerator.job;

import com.videogenerator.api.*;
import com.videogenerator.channel.*;
import com.videogenerator.model.*;
import com.videogenerator.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobPipelineTest {
    static final String STORY_JSON = StoryWriterTest.LLM_JSON; // 3 sahneli fixture (public yap)
    static final String LOC_JSON = """
        {"narrations":["a.","b.","c."],
         "metadata":{"title":"T","description":"D","hashtags":["#x"]}}""";

    @Test
    void happyPathEndsPendingReviewWithCosts(@TempDir Path root) throws Exception {
        Path channels = root.resolve("channels"); Files.createDirectories(channels);
        Files.writeString(channels.resolve("ch1.json"),
            ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                                  .replace("\"sceneCount\":6", "\"sceneCount\":3")
                                  .replace("[\"en\",\"es\"]", "[\"en\"]"));

        LlmClient llm = (sys, user) ->
            user.contains("scenes") || user.contains("Write a gripping") ? STORY_JSON : LOC_JSON;
        ImageGenerator img = (p, out) -> { Files.writeString(out, "png"); return out.toFile(); };
        MusicGenerator music = (p, d, out) -> { Files.writeString(out, "mp3"); return out.toFile(); };
        JobPipeline.TtsEngine tts = (text, voice, audioOut, alignOut) -> {
            Files.writeString(audioOut, "mp3");
            Alignment a = FakeAlignments.forText(text); // her karaktere 0.1 sn veren helper
            Files.writeString(alignOut, new com.google.gson.Gson().toJson(a));
            return new ElevenLabsClient.TtsResult(audioOut.toFile(), a);
        };
        JobPipeline.RenderEngine render = (imgs, durs, vo, mus, ass, out) -> {
            Files.writeString(out, "mp4"); return out.toFile();
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        ContentIdea idea = new ContentIdea(); idea.setTitle("Vanished");
        when(ideas.generateIdeas(any(), anyInt())).thenReturn(List.of(idea));
        when(ideas.selectBestIdea(any())).thenReturn(idea);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline pipeline = new JobPipeline(jobs, new ChannelStore(channels),
            llm, img, music, tts, render, new BudgetGuard(tracker, 100.0), tracker, ideas);

        Job job = pipeline.run("ch1");

        assertEquals(JobStatus.PENDING_REVIEW, job.getStatus());
        assertEquals(1, job.getVariants().size());
        assertTrue(Files.exists(jobs.dirFor(job.getJobId()).resolve("renders/en.mp4")));
        assertTrue(Files.exists(jobs.dirFor(job.getJobId()).resolve("scenes/01.png")));
        assertTrue(job.getCost().total() > 0);
        assertEquals(job.getCost().total(), tracker.spentThisMonth(), 1e-9);
        assertTrue(job.getVariants().get(0).getDurationSeconds() > 0);
    }

    @Test
    void budgetBlockPreventsAnyApiCall(@TempDir Path root) throws Exception {
        Path channels = root.resolve("channels"); Files.createDirectories(channels);
        Files.writeString(channels.resolve("ch1.json"),
            ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        tracker.add(100.0); // bütçe dolu
        LlmClient llm = mock(LlmClient.class);
        JobPipeline pipeline = new JobPipeline(new JobStore(root.resolve("jobs")),
            new ChannelStore(channels), llm, null, null, null, null,
            new BudgetGuard(tracker, 100.0), tracker, mock(IdeaGenerator.class));

        assertThrows(IllegalStateException.class, () -> pipeline.run("ch1"));
        verifyNoInteractions(llm);
    }
}
```

`FakeAlignments.forText(String)` helper'ı (`src/test/java/com/videogenerator/model/FakeAlignments.java`): metnin her karakterine `i*0.1 → (i+1)*0.1` zamanı verir.

- [ ] **Step 2: FAIL doğrula**
- [ ] **Step 3: Implementasyon** — akış (her adım sonrası `jobStore.save(job)`):

```java
public Job run(String channelId) {
    ChannelProfile profile = channelStore.load(channelId);
    double estimate = profile.getSceneCount() * Constants.COST_IMAGE_MEDIUM
        + profile.getLanguages().size() * Constants.COST_TTS_PER_1K_CHARS
        + Constants.COST_MUSIC_TRACK + 2 * Constants.COST_LLM_CALL;
    budgetGuard.assertAllows(estimate);          // HERHANGİ bir API çağrısından önce

    Job job = Job.create(channelId);
    Path dir = jobStore.dirFor(job.getJobId());
    try {
        jobStore.save(job);
        // FAZ 1
        ContentIdea idea = ideaGenerator.selectBestIdea(
            ideaGenerator.generateIdeas(profile.getNiche().getTopic(), 5));
        Story story = new StoryWriter(llm).write(idea, profile);
        job.setStory(story); job.getCost().setLlm(Constants.COST_LLM_CALL); jobStore.save(job);

        double imgCost = new SceneImageService(imageGen).generateAll(story, dir.resolve("scenes"));
        job.getCost().setImages(imgCost); jobStore.save(job);

        Path musicPath = dir.resolve("audio/music.mp3");
        if (!Files.exists(musicPath)) {
            Files.createDirectories(musicPath.getParent());
            musicGen.generate("tense ambient background, instrumental, for: " + story.getTitle(),
                profile.getTargetDurationSeconds(), musicPath);
            job.getCost().setMusic(Constants.COST_MUSIC_TRACK);
        }
        job.setMusicFile("audio/music.mp3");
        job.setStatus(JobStatus.RENDERING); jobStore.save(job);

        // FAZ 2 — dil başına
        TranslationService translator = new TranslationService(llm);
        for (String lang : profile.getLanguages()) {
            Path renderOut = dir.resolve("renders/" + lang + ".mp4");
            if (Files.exists(renderOut)) continue;                 // resume
            LocalizedStory loc = translator.localize(story, lang);
            job.getCost().setLlm(job.getCost().getLlm() + Constants.COST_LLM_CALL);

            String text = SceneTimer.joinNarrations(loc.getNarrations());
            Path audioOut = dir.resolve("audio/" + lang + ".mp3");
            Path alignOut = dir.resolve("audio/" + lang + ".alignment.json");
            var tts = ttsEngine.speak(text, profile.getVoiceId(), audioOut, alignOut);
            job.getCost().setTts(job.getCost().getTts()
                + text.length() / 1000.0 * Constants.COST_TTS_PER_1K_CHARS);

            double[] ends = SceneTimer.sceneEndTimes(loc.getNarrations(), tts.alignment());
            double[] durations = SceneTimer.sceneDurations(ends);
            var cues = SubtitleRenderer.buildCues(tts.alignment(), 3);
            java.io.File ass = SubtitleRenderer.write(cues, dir.resolve("subs/" + lang + ".ass"));

            java.util.List<java.io.File> images = story.getScenes().stream()
                .map(s -> dir.resolve(s.getImageFile()).toFile()).toList();
            Files.createDirectories(renderOut.getParent());
            renderEngine.render(images, durations, tts.audioFile(),
                musicPath.toFile(), ass, renderOut);

            LangVariant v = new LangVariant();
            v.setLang(lang); v.setMetadata(loc.getMetadata());
            v.setAudioFile("audio/" + lang + ".mp3");
            v.setAlignmentFile("audio/" + lang + ".alignment.json");
            v.setRenderFile("renders/" + lang + ".mp4");
            v.setDurationSeconds(tts.alignment().totalDuration());
            v.setPublications(new java.util.ArrayList<>());
            job.getVariants().add(v);
            jobStore.save(job);
        }
        costTracker.add(job.getCost().total());
        job.setStatus(JobStatus.PENDING_REVIEW); jobStore.save(job);
        return job;
    } catch (Exception e) {
        job.setStatus(JobStatus.FAILED);
        job.setError(e.getMessage());
        jobStore.save(job);
        throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
    }
}
```

Not: `IdeaGenerator.generateIdeas(String topic, int n)` overload'u yoksa mevcut imzaya uyarla (NicheData alan sürüm varsa `NicheData`'yı topic'ten kur). Mevcut imzayı Task başında oku, testteki mock'u da ona göre uyarla.

- [ ] **Step 4: PASS doğrula** — `mvn -q test -Dtest=JobPipelineTest`
- [ ] **Step 5: Commit** — `git commit -m "feat: JobPipeline orchestrates two-phase multi-language generation"`

---

### Task 14: Main CLI + örnek kanal + DefaultRenderEngine kablolama

**Files:**
- Modify: `src/main/java/com/videogenerator/main/Main.java`
- Create: `src/main/java/com/videogenerator/job/DefaultRenderEngine.java`
- Create: `channels/example-truecrime-en.json.example`
- Test: mevcut testlerin tamamı + derleme

**Interfaces:**
- Consumes: Task 13'ün `JobPipeline` kurucusu.
- Produces: CLI: `java -jar ... generate <channelId>` → pipeline'ı gerçek client'larla kurar, sonucu loglar. `DefaultRenderEngine implements JobPipeline.RenderEngine`: `AudioProcessor.mixVoiceoverAndMusic(voiceover, music, ...)` → `KenBurnsRenderer.render(...)`.

- [ ] **Step 1: DefaultRenderEngine yaz** — voiceover+music'i `AudioProcessor` ile miksle (mevcut ducking config'i), çıkan tek ses dosyasını `KenBurnsRenderer.render`'a ver.
- [ ] **Step 2: Main'e `generate <channelId>` komutu ekle** — `handleCommandLineArgs`'a case; gerçek bağımlılık grafiği: `OpenAiGptClient` (LlmClient), `ImageApiClient`, `MusicApiClient`, `ElevenLabsClient`'a adapter lambda (TtsEngine), `DefaultRenderEngine`, `BudgetGuard(new CostTracker(Path.of(config.getCostsDir())), config.getMonthlyBudgetUsd())`. Eski `generate`/`generate-ai` komutlarını ŞİMDİLİK bırak (Plan 3 silecek); help metnine yeni komutu ekle.
- [ ] **Step 3: Örnek profil dosyası yaz** — Task 2'deki VALID JSON'un `.example` kopyası; `.gitignore`'a `channels/*.json` ekle (`!*.json.example` istisnasıyla) — gerçek profiller token yolu içerir, repo'ya girmez.
- [ ] **Step 4: Tüm testleri koş** — `mvn -q test` → PASS (ffmpeg grubu hariç). Derleme: `mvn -q compile`.
- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat: wire generate <channelId> CLI with real clients and render engine"`

---

## Self-Review Sonucu

- **Spec kapsama:** §3 sınıfları — `BackofficeServer`, `Publisher/YouTubePublisher`, `Main/DailyScheduler` tam entegrasyonu ve silmeler Plan 2-3'te (bilinçli bölme). Kalan her sınıfın task'ı var. §5 akışı Task 13'te birebir; §7 hata/bütçe Task 4+12+13'te; §9 test stratejisi task'lara gömülü.
- **Placeholder taraması:** temiz — her adımda gerçek kod/komut var.
- **Tip tutarlılığı:** `TtsResult` Task 8'de tanımlı, Task 13 aynı adı kullanıyor; `Alignment` alan adları Task 8-9-10 arasında aynı; `StoryWriterTest.LLM_JSON` ve `ChannelStoreTest.VALID` Task 13'te paylaşılıyor (public static yapılmaları Task 13 Step 1 notunda).
