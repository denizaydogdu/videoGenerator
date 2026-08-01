# Shorts Fabrikası — Plan 3/3: Publisher + Scheduler + Temizlik

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Onaylanan işler YouTube'a idempotent şekilde yüklensin (sentetik içerik beyanıyla), günlük zamanlayıcı yeni pipeline'ı beslesin, ölü Sora/Suno kodu silinsin.

**Architecture:** `Publisher` arayüzü platform başına; `PublishService` orkestre eder (varyant × platform, publication kaydı = idempotency işareti, her adımda atomik job.json). Backoffice approve → async publish tetiği. `DailyScheduler` ContentGeneratorService yerine `Runnable` alır.

**Spec:** `docs/superpowers/specs/2026-08-01-shorts-fabrikasi-design.md` §3, §7

## Global Constraints

- Yayın **idempotent**: `PUBLISHED` işaretli varyant+platform çifti asla yeniden yüklenmez
- YouTube upload'a **sentetik içerik beyanı** (`status.containsSyntheticMedia=true`) eklenir
- Kısmi başarısızlıkta durum `PUBLISHING` + `error`; yeniden çalıştırma yalnız eksikleri yükler
- Publisher token'ı **profil başına** (`youtubeTokenFile`)
- Silme işlemi derlemeyi ve 59 testi bozamaz
- Commit'lerde Claude attribution YOK

---

### Task C1: Publisher arayüzü + PublishService

**Files:**
- Create: `src/main/java/com/videogenerator/publish/Publisher.java`
- Create: `src/main/java/com/videogenerator/publish/PublishService.java`
- Test: `src/test/java/com/videogenerator/publish/PublishServiceTest.java`

**Interfaces:**
- `interface Publisher { String platform(); Publication publish(Job job, LangVariant variant, ChannelProfile profile, java.nio.file.Path jobDir) throws Exception; }`
- `PublishService(JobStore, ChannelStore, Map<String,Publisher>)`: `Job publishApproved(String jobId)`
  - Yalnız `APPROVED` veya `PUBLISHING` (resume) durumundan çalışır; aksi `IllegalStateException`
  - Her varyant × `approvedPlatforms`: mevcut `PUBLISHED` publication varsa atla; publisher yoksa publication `status=SKIPPED_NO_PUBLISHER`; başarıda publication `status=PUBLISHED` + url, her publication sonrası `jobStore.save`
  - Hepsi başarılı → `PUBLISHED`; kısmi → `PUBLISHING` + `error`, exception fırlatılır

**Steps:** failing testler (happy path 2 varyant × 1 platform; idempotent re-run publisher'ı ikinci kez çağırmaz; kısmi hata → PUBLISHING + yalnız eksik yeniden) → implement → PASS → review → commit.

### Task C2: YouTubePublisher + sentetik beyan + profil token'ı

**Files:**
- Modify: `src/main/java/com/videogenerator/api/YouTubeApiClient.java` (token dosyası parametreli kurucu; `containsSyntheticMedia=true`)
- Create: `src/main/java/com/videogenerator/publish/YouTubePublisher.java`
- Test: `src/test/java/com/videogenerator/publish/YouTubePublisherTest.java` (upload request gövdesi kurulumunun birim testi — gerçek upload YOK)

**Interfaces:**
- `YouTubePublisher implements Publisher` — `platform()="YOUTUBE"`; render dosyası `jobDir/renders/<lang>.mp4`; metadata varyanttan; kategori/privacy config'ten
- `YouTubeApiClient`: `buildVideoResource(VideoMetadata, boolean syntheticMedia)` (test edilebilir, statik); upload çağrısı bunu kullanır

**Steps:** TDD → review → commit.

### Task C3: Approve → async publish tetiği

**Files:**
- Modify: `src/main/java/com/videogenerator/web/BackofficeServer.java` (approve sonrası `publishLauncher.launch(jobId)`)
- Modify: `src/main/java/com/videogenerator/main/Main.java` (`serve`: publish executor kablosu; CLI `publish <jobId>`)
- Test: `BackofficeServerTest`'e: approve 204 → publishLauncher çağrıldı

**Interfaces:**
- `BackofficeServer` kurucusuna `JobLauncher publishLauncher` eklenir (aynı fonksiyonel arayüz; jobId ile çağrılır)

**Steps:** TDD → review → commit.

### Task C4: DailyScheduler'ı pipeline'a bağla

**Files:**
- Modify: `src/main/java/com/videogenerator/scheduler/DailyScheduler.java` (ContentGeneratorService yerine `Runnable task`)
- Modify: `src/main/java/com/videogenerator/main/Main.java` (`schedule` komutu: her etkin kanal için `pipeline.run`; üretir, YAYINLAMAZ)
- Test: `src/test/java/com/videogenerator/scheduler/DailySchedulerTest.java` (Runnable çağrısı — zamanlama mantığına dokunmadan kurucu değişikliği)

**Steps:** TDD → review → commit.

### Task C5: Ölü kod temizliği

**Files:**
- Delete: `OpenAiSoraClient`, `SunoApiClient`, `ContentGeneratorService`, `ScriptWriter`, `TextToSpeechService`, `VoiceoverScript`, `VideoRequest`, `VideoResponse`, `VideoStatus`, `MusicRequest`, `MusicResponse`, `ProcessingStage`
- Modify: `Main.java` (legacy interaktif menü ve `generate-ai`/eski `generate` yolları kaldırılır; kalan komutlar: generate/resume/serve/publish/schedule/validate/help), `Constants` (Suno sabitleri), `Configuration.validate()` (Suno/eski zorunluluklar kalkar; OpenAI + ElevenLabs anahtarları yeni zorunlular)
- Keep: `VideoProcessor` (validateShortsRequirements ileride), `AudioProcessor`, `AudioMixConfig`, `KeywordApiClient`, NicheFinder/TrendAnalyzer zinciri

**Steps:** sil → derle → referans hatalarını temizle → 59+ test PASS → review (silinen şeye bağımlılık kalmadı mı) → commit.

## Self-Review Sonucu

- İdempotency publication kaydı üzerinden (spec §7 "kısmi yayın" birebir)
- `containsSyntheticMedia` beyanı C2'de zorunlu adım
- Temizlik listesi referans analiziyle doğrulanacak (derleme bozulursa liste daraltılır, genişletilmez)
