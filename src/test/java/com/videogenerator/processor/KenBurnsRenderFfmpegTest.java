package com.videogenerator.processor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-ffmpeg integration test. Run explicitly with:
 * mvn test -Dtest=KenBurnsRenderFfmpegTest -Dgroups=ffmpeg
 */
@Tag("ffmpeg")
class KenBurnsRenderFfmpegTest {

    private File solidPng(Path dir, String name, Color color) throws Exception {
        BufferedImage img = new BufferedImage(540, 960, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 540, 960);
        g.dispose();
        File f = dir.resolve(name).toFile();
        ImageIO.write(img, "png", f);
        return f;
    }

    @Test
    void rendersTwoScenesWithAudioAndSubtitles(@TempDir Path dir) throws Exception {
        FFmpegWrapper ffmpeg = new FFmpegWrapper();

        File img1 = solidPng(dir, "01.png", Color.DARK_GRAY);
        File img2 = solidPng(dir, "02.png", Color.BLACK);

        // 4 saniyelik sessiz ses (toplam video ~3.5s: 2.0+2.0-0.5 xfade)
        File silent = dir.resolve("silent.mp3").toFile();
        ffmpeg.executeCommand(List.of(ffmpeg.getFfmpegPath(), "-f", "lavfi",
                "-i", "anullsrc=r=44100:cl=mono", "-t", "4",
                "-q:a", "9", "-y", silent.getAbsolutePath()), "make silent audio");

        Path ass = dir.resolve("test.ass");
        Files.writeString(ass, """
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1080
                PlayResY: 1920

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Outline, Shadow, Alignment, MarginL, MarginR, MarginV
                Style: Default,Arial,72,&H00FFFFFF,&H00000000,&H80000000,-1,4,0,2,60,60,420

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:00.00,0:00:02.00,Default,,0,0,0,,TEST SUBTITLE
                """);

        Path out = dir.resolve("renders/out.mp4");
        File result = new KenBurnsRenderer(ffmpeg).render(
                List.of(img1, img2), new double[]{2.0, 2.0}, silent, ass.toFile(), out);

        assertTrue(result.exists());
        assertTrue(result.length() > 10_000, "render too small: " + result.length());
        double duration = ffmpeg.getMediaDuration(result.getAbsolutePath());
        assertEquals(3.5, duration, 0.6); // 2+2-0.5 xfade, container toleransı
    }
}
