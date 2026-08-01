package com.videogenerator.publish;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Persists a per-day YouTube upload count (file: uploads-&lt;yyyy-MM-dd&gt;.json)
 * so the daily upload limit survives process restarts. Same atomic-write
 * pattern as CostTracker.
 */
public class UploadCounter {
    private final Path dir;
    private final Gson gson = new Gson();

    public UploadCounter(Path dir) {
        this.dir = dir;
    }

    private Path file() {
        return dir.resolve("uploads-" + LocalDate.now() + ".json");
    }

    public synchronized int today() {
        try {
            if (!Files.exists(file())) {
                return 0;
            }
            JsonObject o = gson.fromJson(Files.readString(file()), JsonObject.class);
            return o == null || !o.has("count") ? 0 : o.get("count").getAsInt();
        } catch (IOException | JsonSyntaxException e) {
            return 0; // sayaç okunamazsa muhafazakâr davranmıyoruz: limit ayrıca korur
        }
    }

    public synchronized void increment() {
        try {
            Files.createDirectories(dir);
            int next = today() + 1;
            Path tmp = dir.resolve(file().getFileName() + ".tmp");
            Files.writeString(tmp, "{\"count\": " + next + "}");
            Files.move(tmp, file(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to record upload count", e);
        }
    }
}
