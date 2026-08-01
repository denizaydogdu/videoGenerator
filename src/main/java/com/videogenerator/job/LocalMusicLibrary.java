package com.videogenerator.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * F5 — Telifsiz yerel müzik yatağı. ElevenLabs Music kapalıyken (free
 * plan) assets/music/ altındaki mp3'lerden job'a parça seçer.
 * Seçim jobId hash'iyle DETERMİNİSTİK: resume aynı parçayı alır.
 */
public final class LocalMusicLibrary {
    private static final Logger logger = LoggerFactory.getLogger(LocalMusicLibrary.class);

    private LocalMusicLibrary() {
    }

    /**
     * @return seçilen mp3 yolu; klasör yoksa/boşsa null (voice-only devam)
     */
    public static Path pickFor(String jobId, Path musicDir) {
        if (musicDir == null || !Files.isDirectory(musicDir)) {
            return null;
        }
        try (var files = Files.list(musicDir)) {
            List<Path> tracks = files
                    .filter(f -> f.toString().toLowerCase().endsWith(".mp3"))
                    .sorted() // deterministik sıra
                    .toList();
            if (tracks.isEmpty()) {
                return null;
            }
            int idx = Math.floorMod(jobId.hashCode(), tracks.size());
            Path pick = tracks.get(idx);
            logger.info("Local music picked for {}: {}", jobId, pick.getFileName());
            return pick;
        } catch (IOException e) {
            logger.warn("Cannot list local music dir {}: {}", musicDir, e.getMessage());
            return null;
        }
    }
}
