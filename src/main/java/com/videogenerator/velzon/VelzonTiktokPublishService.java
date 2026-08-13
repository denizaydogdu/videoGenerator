package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * output/velzon-youtube/&lt;batchId&gt;/ altındaki, YouTube için ZATEN
 * render edilmiş videoları ikinci bir hesaba (@velzon_tr) TikTok Direct
 * Post ile yayınlar. Video ÜRETMEZ — VelzonYoutubePublishService'in
 * "Yayınla" akışı zaten video-XX.mp4'ü diskte bırakıyor (cleanup yalnız
 * ara görsel/ses dosyalarını siler, bkz. VelzonYoutubeVideoBuilder), bu
 * sınıf sadece o dosyayı ikinci platforma gönderir.
 *
 * Ayrı durum dosyası: mevcut manifest.json'daki YouTube'a özgü
 * published/url alanlarına dokunmadan, her batch dizininde kendi
 * "tiktok.json" dosyasında publish durumunu tutar (index -> {published,
 * publishId}) — iki platformun yayın durumu birbirinden bağımsız.
 *
 * KRİTİK sıralama (Instagram/YouTube'daki emsal bug fix'in aynısı):
 * TikTok'a post GERİ ALINAMAZ bir adımdır (uygulama onayına kadar
 * hesabın inbox'ına düşse bile). Post başarılı olur olmaz
 * tiktokPublished=true+publishId hemen diske yazılır.
 */
public class VelzonTiktokPublishService {
    private static final Logger logger = LoggerFactory.getLogger(VelzonTiktokPublishService.class);

    /** TikTok'a video gönderme seti — testte sahte implementasyonla değiştirilebilir. */
    public interface Poster {
        /** Başarılı olursa TikTok'un publish_id'sini döner (paylaşılabilir bir URL değil). */
        String post(Path video, String title, String privacyLevel) throws Exception;
    }

    public record ScriptEntry(String title, List<String> hashtags, boolean videoReady,
                              boolean tiktokPublished, String tiktokPublishId) {
    }

    public record Batch(String id, List<ScriptEntry> scripts) {
    }

    private final Poster poster;
    private final Path youtubeDir;
    private final String privacyLevel;
    private final Gson gson = new Gson();

    public VelzonTiktokPublishService(Poster poster, Path youtubeDir, String privacyLevel) {
        this.poster = poster;
        this.youtubeDir = youtubeDir;
        this.privacyLevel = privacyLevel;
    }

    public List<Batch> listBatches() throws Exception {
        if (!Files.isDirectory(youtubeDir)) {
            return List.of();
        }
        List<Batch> out = new ArrayList<>();
        try (var stream = Files.list(youtubeDir)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve("manifest.json");
                if (Files.exists(manifestPath)) {
                    out.add(new Batch(dir.getFileName().toString(), readScripts(dir, manifestPath)));
                }
            }
        }
        return out;
    }

    /**
     * Zaten TikTok'a gönderilmiş içeriği tekrar postlamaz (idempotent).
     * Video henüz render edilmemişse (YouTube'a hiç "Yayınla" denenmemişse)
     * fırlatır — video inşası bu sınıfın sorumluluğunda değil.
     */
    public ScriptEntry publishToTiktok(String batchId, int index) throws Exception {
        Path dir = resolveBatchDir(batchId);
        Path manifestPath = dir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        List<ScriptEntry> scripts = readScripts(dir, manifestPath);
        if (index < 0 || index >= scripts.size()) {
            throw new IllegalArgumentException("Script index out of range: " + index);
        }
        ScriptEntry entry = scripts.get(index);
        if (entry.tiktokPublished()) {
            logger.info("Already published to TikTok, skipping: {} #{}", batchId, index);
            return entry;
        }
        if (!entry.videoReady()) {
            throw new IllegalStateException(
                    "Video not rendered yet — publish to YouTube first: " + batchId + " #" + index);
        }

        Path video = dir.resolve(videoFileName(dir, index));
        String publishId = poster.post(video, entry.title(), privacyLevel);

        // TikTok'a post ZATEN gönderildi, geri alınamaz. published=true +
        // publishId burada hemen kalıcı hale getirilir — aksi halde
        // kullanıcı tekrar "Yayınla" derse aynı video ikinci kez postlanır.
        ScriptEntry updated = new ScriptEntry(entry.title(), entry.hashtags(), true, true, publishId);
        writeTiktokState(dir, index, updated);

        logger.info("TikTok post published: {} #{} -> {}", batchId, index, publishId);
        return updated;
    }

    private String videoFileName(Path dir, int index) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(dir.resolve("manifest.json")), JsonArray.class);
        String imageFile = arr.get(index).getAsJsonObject().get("imageFile").getAsString();
        int dot = imageFile.lastIndexOf('.');
        return (dot < 0 ? imageFile : imageFile.substring(0, dot)) + ".mp4";
    }

    private Path resolveBatchDir(String batchId) {
        if (batchId == null || batchId.contains("/") || batchId.contains("\\")
                || batchId.contains("..")) {
            throw new IllegalArgumentException("Invalid batchId: " + batchId);
        }
        Path dir = youtubeDir.resolve(batchId).normalize();
        if (!dir.startsWith(youtubeDir.normalize())) {
            throw new IllegalArgumentException("Path escapes directory: " + batchId);
        }
        return dir;
    }

    private List<ScriptEntry> readScripts(Path dir, Path manifestPath) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(manifestPath), JsonArray.class);
        JsonObject tiktokState = readTiktokState(dir);
        List<ScriptEntry> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject o = arr.get(i).getAsJsonObject();
            List<String> hashtags = new ArrayList<>();
            if (o.has("hashtags")) {
                for (var tag : o.getAsJsonArray("hashtags")) {
                    hashtags.add(tag.getAsString());
                }
            }
            String imageFile = o.get("imageFile").getAsString();
            int dot = imageFile.lastIndexOf('.');
            String videoFile = (dot < 0 ? imageFile : imageFile.substring(0, dot)) + ".mp4";
            boolean videoReady = Files.exists(dir.resolve(videoFile));

            boolean tiktokPublished = false;
            String tiktokPublishId = null;
            String key = String.valueOf(i);
            if (tiktokState.has(key)) {
                JsonObject state = tiktokState.getAsJsonObject(key);
                tiktokPublished = state.has("published") && state.get("published").getAsBoolean();
                tiktokPublishId = state.has("publishId") ? state.get("publishId").getAsString() : null;
            }
            out.add(new ScriptEntry(o.get("title").getAsString(), hashtags, videoReady,
                    tiktokPublished, tiktokPublishId));
        }
        return out;
    }

    private JsonObject readTiktokState(Path dir) throws Exception {
        Path stateFile = dir.resolve("tiktok.json");
        if (!Files.exists(stateFile)) {
            return new JsonObject();
        }
        JsonObject o = gson.fromJson(Files.readString(stateFile), JsonObject.class);
        return o != null ? o : new JsonObject();
    }

    private void writeTiktokState(Path dir, int index, ScriptEntry updated) throws Exception {
        JsonObject state = readTiktokState(dir);
        JsonObject entryState = new JsonObject();
        entryState.addProperty("published", updated.tiktokPublished());
        entryState.addProperty("publishId", updated.tiktokPublishId());
        state.add(String.valueOf(index), entryState);

        Path stateFile = dir.resolve("tiktok.json");
        Path tmp = Files.createTempFile(dir, "tiktok", ".tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(state));
        Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
