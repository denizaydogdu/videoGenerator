package com.videogenerator.channel;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads and validates channel profiles from a directory of JSON files.
 */
public class ChannelStore {
    private static final Logger logger = LoggerFactory.getLogger(ChannelStore.class);
    private final Path dir;
    private final Gson gson = new Gson();

    public ChannelStore(Path dir) {
        this.dir = dir;
    }

    /**
     * Loads a single channel profile by id (file: <channelId>.json).
     *
     * @throws IllegalArgumentException if the file is unreadable or invalid
     */
    public ChannelProfile load(String channelId) {
        if (channelId == null || channelId.contains("/") || channelId.contains("\\")
                || channelId.contains("..")) {
            throw new IllegalArgumentException("Invalid channelId: " + channelId);
        }
        Path file = dir.resolve(channelId + ".json").normalize();
        if (!file.startsWith(dir.normalize())) {
            throw new IllegalArgumentException("Channel path escapes directory: " + channelId);
        }
        try {
            ChannelProfile profile = gson.fromJson(Files.readString(file), ChannelProfile.class);
            if (profile == null) {
                throw new IllegalArgumentException("Empty or null channel profile: " + channelId);
            }
            profile.validate();
            return profile;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read channel: " + channelId, e);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed channel JSON: " + channelId, e);
        }
    }

    /** Backoffice'ten düzenlenebilir alanlar — kimlik/token alanları hariç. */
    private static final java.util.Set<String> EDITABLE_FIELDS = java.util.Set.of(
            "displayName", "stylePrefix", "voiceId", "niche", "languages",
            "platforms", "targetDurationSeconds", "sceneCount", "enabled", "meta");

    /**
     * Whitelist'li alanları mevcut JSON'a merge eder, doğrular ve atomik
     * yazar. Geçersiz sonuç dosyaya dokunmadan reddedilir.
     */
    public ChannelProfile update(String channelId,
                                 com.google.gson.JsonObject patch) throws IOException {
        load(channelId); // id doğrulama + mevcut dosyanın geçerliliği
        Path file = dir.resolve(channelId + ".json");
        com.google.gson.JsonObject current = gson.fromJson(
                Files.readString(file), com.google.gson.JsonObject.class);
        for (String key : patch.keySet()) {
            if (EDITABLE_FIELDS.contains(key)) {
                current.add(key, patch.get(key));
            }
        }
        ChannelProfile candidate = gson.fromJson(current, ChannelProfile.class);
        candidate.validate(); // throws -> dosya değişmeden kalır
        Path tmp = Files.createTempFile(dir, channelId, ".tmp");
        Files.writeString(tmp, new com.google.gson.GsonBuilder()
                .setPrettyPrinting().create().toJson(current));
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        logger.info("Channel updated: {} ({})", channelId, patch.keySet());
        return candidate;
    }

    /**
     * Loads all enabled channel profiles in the directory.
     */
    public List<ChannelProfile> loadEnabled() throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".json"))
                    .map(f -> {
                        try {
                            return gson.fromJson(Files.readString(f), ChannelProfile.class);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException("Cannot read " + f, e);
                        } catch (com.google.gson.JsonSyntaxException e) {
                            throw new IllegalArgumentException("Malformed channel JSON: " + f, e);
                        }
                    })
                    .filter(p -> {
                        if (p == null) {
                            logger.warn("Skipping empty/null channel profile file in {}", dir);
                            return false;
                        }
                        return p.isEnabled();
                    })
                    .peek(ChannelProfile::validate)
                    .peek(p -> logger.info("Channel loaded: {}", p.getChannelId()))
                    .toList();
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
