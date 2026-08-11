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
 * output/velzon/<batchId>/manifest.json dosyalarını okuyup backoffice'e
 * taslak listesi sunar, "Yayınla" tıklamasını XApiClient'a bağlar.
 * Pinterest'in publish-service desenini izler; görsel/pano yok (metin-only).
 */
public class VelzonPublishService {
    private static final Logger logger = LoggerFactory.getLogger(VelzonPublishService.class);

    private final XApiClient client;
    private final Path velzonDir;
    private final Gson gson = new Gson();

    public record TweetEntry(String topic, String text, boolean published, String url) {
    }

    public record Batch(String id, List<TweetEntry> tweets) {
    }

    public VelzonPublishService(XApiClient client, Path velzonDir) {
        this.client = client;
        this.velzonDir = velzonDir;
    }

    public List<Batch> listBatches() throws Exception {
        if (!Files.isDirectory(velzonDir)) {
            return List.of();
        }
        List<Batch> out = new ArrayList<>();
        try (var stream = Files.list(velzonDir)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve("manifest.json");
                if (Files.exists(manifestPath)) {
                    out.add(new Batch(dir.getFileName().toString(), readManifest(manifestPath)));
                }
            }
        }
        return out;
    }

    /** Zaten yayınlanmış tweet'i tekrar API'ye göndermez (idempotent). */
    public TweetEntry publishTweet(String batchId, int index) throws Exception {
        Path dir = resolveBatchDir(batchId);
        Path manifestPath = dir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        List<TweetEntry> tweets = readManifest(manifestPath);
        if (index < 0 || index >= tweets.size()) {
            throw new IllegalArgumentException("Tweet index out of range: " + index);
        }
        TweetEntry entry = tweets.get(index);
        if (entry.published()) {
            logger.info("Tweet already published, skipping: {} #{}", batchId, index);
            return entry;
        }

        String url = client.postTweet(entry.text());
        TweetEntry updated = new TweetEntry(entry.topic(), entry.text(), true, url);
        tweets.set(index, updated);
        writeManifest(manifestPath, tweets);
        logger.info("Tweet published: {} #{} -> {}", batchId, index, url);
        return updated;
    }

    private Path resolveBatchDir(String batchId) {
        if (batchId == null || batchId.contains("/") || batchId.contains("\\")
                || batchId.contains("..")) {
            throw new IllegalArgumentException("Invalid batchId: " + batchId);
        }
        Path dir = velzonDir.resolve(batchId).normalize();
        if (!dir.startsWith(velzonDir.normalize())) {
            throw new IllegalArgumentException("Path escapes directory: " + batchId);
        }
        return dir;
    }

    private List<TweetEntry> readManifest(Path manifestPath) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(manifestPath), JsonArray.class);
        List<TweetEntry> out = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.add(new TweetEntry(
                    o.get("topic").getAsString(),
                    o.get("text").getAsString(),
                    o.has("published") && o.get("published").getAsBoolean(),
                    o.has("url") ? o.get("url").getAsString() : null));
        }
        return out;
    }

    private void writeManifest(Path manifestPath, List<TweetEntry> tweets) throws Exception {
        JsonArray arr = new JsonArray();
        for (TweetEntry t : tweets) {
            JsonObject o = new JsonObject();
            o.addProperty("topic", t.topic());
            o.addProperty("text", t.text());
            o.addProperty("published", t.published());
            if (t.url() != null) {
                o.addProperty("url", t.url());
            }
            arr.add(o);
        }
        Path tmp = Files.createTempFile(manifestPath.getParent(), "manifest", ".tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        Files.move(tmp, manifestPath, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
