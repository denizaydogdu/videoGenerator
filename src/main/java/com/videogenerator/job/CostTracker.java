package com.videogenerator.job;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;

/**
 * Persists monthly API spend to output/costs/&lt;yyyy-MM&gt;.json.
 * Cross-process safe: updates hold an OS-level FileLock so two JVMs
 * (pipeline + backoffice) cannot lose each other's writes.
 * Reads fail CLOSED: an existing-but-corrupt cost file throws instead of
 * silently reporting zero spend, because the budget guard depends on it.
 */
public class CostTracker {
    private final Path dir;
    private final Gson gson = new Gson();

    public CostTracker(Path dir) {
        this.dir = dir;
    }

    private Path fileFor(YearMonth month) {
        return dir.resolve(month + ".json");
    }

    public synchronized void add(double usd) {
        YearMonth month = YearMonth.now(); // captured once: no mid-call rollover
        try {
            Files.createDirectories(dir);
            Path lockPath = dir.resolve(month + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                double next = readSpent(month) + usd;
                Path tmp = dir.resolve(month + ".json.tmp");
                Files.writeString(tmp, "{\"spent\": " + next + "}");
                Files.move(tmp, fileFor(month),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to record cost", e);
        }
    }

    public synchronized double spentThisMonth() {
        return readSpent(YearMonth.now());
    }

    private double readSpent(YearMonth month) {
        Path file = fileFor(month);
        if (!Files.exists(file)) {
            return 0.0;
        }
        try {
            JsonObject o = gson.fromJson(Files.readString(file), JsonObject.class);
            if (o == null || !o.has("spent")) {
                throw new IllegalStateException("Corrupt cost file (fail closed): " + file);
            }
            return o.get("spent").getAsDouble();
        } catch (IOException | JsonSyntaxException e) {
            throw new IllegalStateException("Unreadable cost file (fail closed): " + file, e);
        }
    }
}
