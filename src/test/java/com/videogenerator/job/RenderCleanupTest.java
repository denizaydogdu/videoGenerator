package com.videogenerator.job;

import com.videogenerator.processor.AudioProcessor;
import com.videogenerator.processor.KenBurnsRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RenderCleanupTest {
    @Test
    void mixFileDeletedAfterSuccessfulRender(@TempDir Path dir) throws Exception {
        Path jobDir = dir.resolve("jobs/2026-08-01-x/renders");
        Files.createDirectories(jobDir);
        File mixed = Files.createFile(dir.resolve("mix.mp3")).toFile();

        AudioProcessor audio = mock(AudioProcessor.class);
        when(audio.mixVoiceoverAndMusic(any(), any(), anyString())).thenReturn(mixed);
        KenBurnsRenderer kenBurns = mock(KenBurnsRenderer.class);
        Path out = jobDir.resolve("en.mp4");
        when(kenBurns.render(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Files.writeString(out, "mp4");
                    return out.toFile();
                });

        new DefaultRenderEngine(audio, kenBurns).render(
                List.of(new File("a.png")), new double[]{1.0},
                new File("vo.mp3"), new File("music.mp3"), new File("s.ass"), out);

        assertFalse(mixed.exists(), "geçici miks dosyası render sonrası silinmeli");
        assertTrue(Files.exists(out));
    }
}
