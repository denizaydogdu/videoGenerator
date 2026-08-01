package com.videogenerator.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F5 — Yerel müzik yatağı: ElevenLabs müzik kapalıyken assets/music/
 * içindeki telifsiz mp3'lerden job'a deterministik seçim.
 */
class LocalMusicTest {
    @Test
    void picksDeterministicTrackForJob(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("dark-pulse.mp3"), "a");
        Files.writeString(dir.resolve("tension-bed.mp3"), "b");
        Files.writeString(dir.resolve("not-music.txt"), "x");

        Path pick1 = LocalMusicLibrary.pickFor("job-abc", dir);
        Path pick2 = LocalMusicLibrary.pickFor("job-abc", dir);
        assertEquals(pick1, pick2, "aynı job her zaman aynı parçayı almalı (resume)");
        assertTrue(pick1.toString().endsWith(".mp3"));
    }

    @Test
    void emptyOrMissingDirReturnsNull(@TempDir Path dir) {
        assertNull(LocalMusicLibrary.pickFor("job-abc", dir.resolve("yok")));
        assertNull(LocalMusicLibrary.pickFor("job-abc", dir)); // boş klasör
    }
}
