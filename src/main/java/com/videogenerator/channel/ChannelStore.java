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
