package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * output/velzon-youtube/&lt;batchId&gt;/manifest.json dosyalarını okuyup
 * backoffice'e parti listesi sunar, "Yayınla" tıklamasını video
 * inşası + YouTubeApiClient.uploadVideo çağrısına bağlar — Instagram'ın
 * publish-service desenini izler.
 *
 * TEMBEL RENDER: VelzonYoutubeScriptGenerator parti üretiminde görsel/ses/
 * video ÜRETMEZ (bkz. o sınıfın javadoc'u) — bu servis "Yayınla" tıklandığında
 * {@link VideoBuilder} aracılığıyla video'yu (yoksa) inşa eder, sonra
 * yükler. VideoBuilder kendi içinde dosya varlığına bakarak idempotent
 * çalışmalı (yarım kalan bir denemenin ürettiği görsel/ses/video tekrar
 * üretilmemeli) — bkz. VelzonYoutubeVideoBuilder.
 *
 * KRİTİK sıralama (VelzonInstagramPublishService'teki emsal bug fix'in
 * aynısı): YouTube'a upload GERİ ALINAMAZ bir adımdır. Upload başarılı olur
 * olmaz, published=true+url hemen diske yazılır — bundan SONRA çalışan her
 * şey (örn. ara dosyaların temizliği) patlasa bile kullanıcı tekrar
 * "Yayınla" derse aynı video ikinci kez yüklenmez.
 */
public class VelzonYoutubePublishService {
    private static final Logger logger = LoggerFactory.getLogger(VelzonYoutubePublishService.class);

    /** Video inşası (görsel + seslendirme + Ken Burns render) seti — testte sahte implementasyonla değiştirilebilir. */
    public interface VideoBuilder {
        File build(Path batchDir, ScriptEntry entry, int index) throws Exception;

        /** Upload sonrası en iyi çaba temizliği (ör. ara görsel/ses dosyaları). Varsayılan: hiçbir şey yapma. */
        default void cleanup(Path batchDir, ScriptEntry entry, int index) throws Exception {
        }
    }

    /** Minimal upload seam — gerçek impl YouTubeApiClient.uploadVideo'yu sarar. */
    public interface Uploader {
        UploadResult upload(File videoFile, VideoMetadata metadata) throws Exception;
    }

    private final Uploader uploader;
    private final VideoBuilder videoBuilder;
    private final Path youtubeDir;
    private final Gson gson = new Gson();

    public record ScriptEntry(String narration, String title, String description,
                              List<String> hashtags, String imagePrompt, String imageFile,
                              boolean published, String url) {
    }

    public record Batch(String id, List<ScriptEntry> scripts) {
    }

    public VelzonYoutubePublishService(Uploader uploader, VideoBuilder videoBuilder, Path youtubeDir) {
        this.uploader = uploader;
        this.videoBuilder = videoBuilder;
        this.youtubeDir = youtubeDir;
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
                    out.add(new Batch(dir.getFileName().toString(), readManifest(manifestPath)));
                }
            }
        }
        return out;
    }

    /**
     * Zaten yayınlanmış videoyu tekrar inşa etmez/yüklemez (idempotent).
     * Yayınlanmamışsa: video (yoksa) inşa edilir, YouTube'a yüklenir,
     * published=true HEMEN kalıcı hale getirilir (bkz. sınıf javadoc'u).
     */
    public ScriptEntry publishVideo(String batchId, int index) throws Exception {
        Path dir = resolveBatchDir(batchId);
        Path manifestPath = dir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        List<ScriptEntry> scripts = readManifest(manifestPath);
        if (index < 0 || index >= scripts.size()) {
            throw new IllegalArgumentException("Script index out of range: " + index);
        }
        ScriptEntry entry = scripts.get(index);
        if (entry.published()) {
            logger.info("Video already published, skipping: {} #{}", batchId, index);
            return entry;
        }

        File videoFile = videoBuilder.build(dir, entry, index);

        VideoMetadata metadata = new VideoMetadata(entry.title(), entry.description(), entry.hashtags());
        metadata.setLanguage("tr");

        UploadResult result = uploader.upload(videoFile, metadata);
        String url = result.getShortsUrl() != null ? result.getShortsUrl() : result.getUrl();

        // Video artık YouTube'da CANLI — geri alınamaz. Aşağıdaki cleanup
        // adımı başarısız olsa bile published=true+url burada hemen kalıcı
        // hale getirilir; aksi halde kullanıcı "Yayınla"ya tekrar basarsa aynı
        // video ikinci kez yüklenir (bkz. Instagram'daki emsal bug).
        ScriptEntry updated = new ScriptEntry(entry.narration(), entry.title(), entry.description(),
                entry.hashtags(), entry.imagePrompt(), entry.imageFile(), true, url);
        scripts.set(index, updated);
        writeManifest(manifestPath, scripts);

        try {
            videoBuilder.cleanup(dir, entry, index);
        } catch (Exception e) {
            logger.warn("Video published but intermediate cleanup failed: {} #{}", batchId, index, e);
        }

        logger.info("YouTube video published: {} #{} -> {}", batchId, index, url);
        return updated;
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

    private List<ScriptEntry> readManifest(Path manifestPath) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(manifestPath), JsonArray.class);
        List<ScriptEntry> out = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            List<String> hashtags = new ArrayList<>();
            if (o.has("hashtags")) {
                for (var tag : o.getAsJsonArray("hashtags")) {
                    hashtags.add(tag.getAsString());
                }
            }
            out.add(new ScriptEntry(
                    o.get("narration").getAsString(),
                    o.get("title").getAsString(),
                    o.get("description").getAsString(),
                    hashtags,
                    o.get("imagePrompt").getAsString(),
                    o.get("imageFile").getAsString(),
                    o.has("published") && o.get("published").getAsBoolean(),
                    o.has("url") ? o.get("url").getAsString() : null));
        }
        return out;
    }

    private void writeManifest(Path manifestPath, List<ScriptEntry> scripts) throws Exception {
        JsonArray arr = new JsonArray();
        for (ScriptEntry s : scripts) {
            JsonObject o = new JsonObject();
            o.addProperty("narration", s.narration());
            o.addProperty("title", s.title());
            o.addProperty("description", s.description());
            JsonArray tags = new JsonArray();
            s.hashtags().forEach(tags::add);
            o.add("hashtags", tags);
            o.addProperty("imagePrompt", s.imagePrompt());
            o.addProperty("imageFile", s.imageFile());
            o.addProperty("published", s.published());
            if (s.url() != null) {
                o.addProperty("url", s.url());
            }
            arr.add(o);
        }
        Path tmp = Files.createTempFile(manifestPath.getParent(), "manifest", ".tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
