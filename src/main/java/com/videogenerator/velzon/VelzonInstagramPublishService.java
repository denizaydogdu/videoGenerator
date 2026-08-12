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
 * output/velzon-instagram/<batchId>/manifest.json dosyalarını okuyup
 * backoffice'e parti listesi sunar, "Yayınla" tıklamasını
 * VelzonInstagramApiClient'ın üç adımına bağlar (container -> publish ->
 * permalink) — Pinterest'in publish-service desenini izler.
 *
 * Instagram'ın image_url'i PUBLİK erişilebilir olmalı (bkz.
 * VelzonInstagramApiClient javadoc) — bu servis, backoffice'in kendi
 * görsel servis endpoint'ini (imageFile/HTTP route) publicBaseUrl ile
 * birleştirerek Instagram'ın indirebileceği tam URL'i inşa eder.
 * publicBaseUrl 127.0.0.1 DEĞİL, gerçek prod domain'i olmalı
 * (ör. https://shorts.velzon.tr) — aksi halde Instagram sunucuları
 * görseli indiremez.
 */
public class VelzonInstagramPublishService {
    private static final Logger logger = LoggerFactory.getLogger(VelzonInstagramPublishService.class);

    private final VelzonInstagramApiClient client;
    private final Path igDir;
    private final String publicBaseUrl;
    private final Gson gson = new Gson();

    public record PostEntry(String file, String caption, boolean published, String url) {
    }

    public record Batch(String id, List<PostEntry> posts) {
    }

    public VelzonInstagramPublishService(VelzonInstagramApiClient client, Path igDir,
                                         String publicBaseUrl) {
        this.client = client;
        this.igDir = igDir;
        this.publicBaseUrl = publicBaseUrl;
    }

    public List<Batch> listBatches() throws Exception {
        if (!Files.isDirectory(igDir)) {
            return List.of();
        }
        List<Batch> out = new ArrayList<>();
        try (var stream = Files.list(igDir)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve("manifest.json");
                if (Files.exists(manifestPath)) {
                    out.add(new Batch(dir.getFileName().toString(), readManifest(manifestPath)));
                }
            }
        }
        return out;
    }

    /** Zaten yayınlanmış gönderiyi tekrar API'ye göndermez (idempotent). */
    public PostEntry publishPost(String batchId, int index) throws Exception {
        Path dir = resolveBatchDir(batchId);
        Path manifestPath = dir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        List<PostEntry> posts = readManifest(manifestPath);
        if (index < 0 || index >= posts.size()) {
            throw new IllegalArgumentException("Post index out of range: " + index);
        }
        PostEntry entry = posts.get(index);
        if (entry.published()) {
            logger.info("Post already published, skipping: {} #{}", batchId, index);
            return entry;
        }

        String imageUrl = publicBaseUrl + "/api/velzon-instagram/batches/" + batchId
                + "/images/" + entry.file();
        String creationId = client.createMediaContainer(imageUrl, entry.caption());
        String mediaId = client.publishContainer(creationId);

        // Post gerçek hesapta artık CANLI — geri alınamaz. Permalink çekme veya
        // manifest yazma bundan sonra başarısız olsa bile published=true burada
        // hemen kalıcı hale getirilir; aksi halde kullanıcı "Yayınla"ya tekrar
        // basarsa aynı gönderi ikinci kez paylaşılır (bkz. Pinterest'teki emsal bug).
        PostEntry publishedNoUrl = new PostEntry(entry.file(), entry.caption(), true, null);
        posts.set(index, publishedNoUrl);
        writeManifest(manifestPath, posts);

        String permalink;
        try {
            permalink = client.getPermalink(mediaId);
        } catch (Exception e) {
            logger.warn("Instagram post published but permalink fetch failed: {} #{}",
                    batchId, index, e);
            return publishedNoUrl;
        }

        PostEntry updated = new PostEntry(entry.file(), entry.caption(), true, permalink);
        posts.set(index, updated);
        writeManifest(manifestPath, posts);
        logger.info("Instagram post published: {} #{} -> {}", batchId, index, permalink);
        return updated;
    }

    /** Backoffice küçük resim servisi için — path traversal korumalı. */
    public Path imageFile(String batchId, String file) {
        Path dir = resolveBatchDir(batchId);
        if (file == null || file.contains("/") || file.contains("\\") || file.contains("..")) {
            throw new IllegalArgumentException("Invalid file: " + file);
        }
        return dir.resolve(file);
    }

    private Path resolveBatchDir(String batchId) {
        if (batchId == null || batchId.contains("/") || batchId.contains("\\")
                || batchId.contains("..")) {
            throw new IllegalArgumentException("Invalid batchId: " + batchId);
        }
        Path dir = igDir.resolve(batchId).normalize();
        if (!dir.startsWith(igDir.normalize())) {
            throw new IllegalArgumentException("Path escapes directory: " + batchId);
        }
        return dir;
    }

    private List<PostEntry> readManifest(Path manifestPath) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(manifestPath), JsonArray.class);
        List<PostEntry> out = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.add(new PostEntry(
                    o.get("file").getAsString(),
                    o.get("caption").getAsString(),
                    o.has("published") && o.get("published").getAsBoolean(),
                    o.has("url") ? o.get("url").getAsString() : null));
        }
        return out;
    }

    private void writeManifest(Path manifestPath, List<PostEntry> posts) throws Exception {
        JsonArray arr = new JsonArray();
        for (PostEntry p : posts) {
            JsonObject o = new JsonObject();
            o.addProperty("file", p.file());
            o.addProperty("caption", p.caption());
            o.addProperty("published", p.published());
            if (p.url() != null) {
                o.addProperty("url", p.url());
            }
            arr.add(o);
        }
        Path tmp = Files.createTempFile(manifestPath.getParent(), "manifest", ".tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
