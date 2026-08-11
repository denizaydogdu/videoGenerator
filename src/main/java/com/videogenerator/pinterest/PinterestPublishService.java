package com.videogenerator.pinterest;

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
 * output/pinterest/<batchId>/manifest.json dosyalarını okuyup backoffice'e
 * parti listesi sunar, "Yayınla" tıklamasını PinterestApiClient'a bağlar.
 * board_id her çağrıda sorgulanmaz — ilk yayında bulunur, bellekte tutulur.
 */
public class PinterestPublishService {
    private static final Logger logger = LoggerFactory.getLogger(PinterestPublishService.class);

    private final PinterestApiClient client;
    private final String boardName;
    private final Path pinterestDir;
    private final Gson gson = new Gson();
    private volatile String cachedBoardId;

    public record PinEntry(String file, String title, String description, String altText,
                           boolean published, String url) {
    }

    public record Batch(String id, List<PinEntry> pins) {
    }

    public PinterestPublishService(PinterestApiClient client, String boardName, Path pinterestDir) {
        this.client = client;
        this.boardName = boardName;
        this.pinterestDir = pinterestDir;
    }

    public List<Batch> listBatches() throws Exception {
        if (!Files.isDirectory(pinterestDir)) {
            return List.of();
        }
        List<Batch> out = new ArrayList<>();
        try (var stream = Files.list(pinterestDir)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                Path manifestPath = dir.resolve("manifest.json");
                if (Files.exists(manifestPath)) {
                    out.add(new Batch(dir.getFileName().toString(), readManifest(manifestPath)));
                }
            }
        }
        return out;
    }

    /** Zaten yayınlanmış pini tekrar API'ye göndermez (idempotent). */
    public PinEntry publishPin(String batchId, int index) throws Exception {
        Path dir = resolveBatchDir(batchId);
        Path manifestPath = dir.resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        List<PinEntry> pins = readManifest(manifestPath);
        if (index < 0 || index >= pins.size()) {
            throw new IllegalArgumentException("Pin index out of range: " + index);
        }
        PinEntry entry = pins.get(index);
        if (entry.published()) {
            logger.info("Pin already published, skipping: {} #{}", batchId, index);
            return entry;
        }

        String url = client.createPin(dir.resolve(entry.file()), boardId(),
                entry.title(), entry.description(), entry.altText());
        PinEntry updated = new PinEntry(entry.file(), entry.title(), entry.description(),
                entry.altText(), true, url);
        pins.set(index, updated);
        writeManifest(manifestPath, pins);
        logger.info("Pin published: {} #{} -> {}", batchId, index, url);
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

    private synchronized String boardId() throws Exception {
        if (cachedBoardId == null) {
            cachedBoardId = client.findBoardIdByName(boardName);
        }
        return cachedBoardId;
    }

    private Path resolveBatchDir(String batchId) {
        if (batchId == null || batchId.contains("/") || batchId.contains("\\")
                || batchId.contains("..")) {
            throw new IllegalArgumentException("Invalid batchId: " + batchId);
        }
        Path dir = pinterestDir.resolve(batchId).normalize();
        if (!dir.startsWith(pinterestDir.normalize())) {
            throw new IllegalArgumentException("Path escapes directory: " + batchId);
        }
        return dir;
    }

    private List<PinEntry> readManifest(Path manifestPath) throws Exception {
        JsonArray arr = gson.fromJson(Files.readString(manifestPath), JsonArray.class);
        List<PinEntry> out = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.add(new PinEntry(
                    o.get("file").getAsString(),
                    o.get("title").getAsString(),
                    o.get("description").getAsString(),
                    o.has("altText") ? o.get("altText").getAsString() : "",
                    o.has("published") && o.get("published").getAsBoolean(),
                    o.has("url") ? o.get("url").getAsString() : null));
        }
        return out;
    }

    private void writeManifest(Path manifestPath, List<PinEntry> pins) throws Exception {
        JsonArray arr = new JsonArray();
        for (PinEntry p : pins) {
            JsonObject o = new JsonObject();
            o.addProperty("file", p.file());
            o.addProperty("title", p.title());
            o.addProperty("description", p.description());
            o.addProperty("altText", p.altText());
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
