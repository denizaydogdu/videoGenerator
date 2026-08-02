package com.videogenerator.processor;

import com.videogenerator.util.VideoProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders scene images into a 1080x1920 video with Ken Burns motion
 * (alternating zoom in/out), crossfade transitions and burned-in subtitles.
 */
public class KenBurnsRenderer {
    private static final Logger logger = LoggerFactory.getLogger(KenBurnsRenderer.class);
    private static final double XFADE_SECONDS = 0.5;
    private static final int FPS = 30;

    private final FFmpegWrapper ffmpeg;

    public KenBurnsRenderer(FFmpegWrapper ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /**
     * Builds the -filter_complex graph. Even scene indexes zoom in
     * (1.0 -> 1.10), odd indexes zoom out (1.10 -> 1.0); scenes crossfade
     * and the subtitles filter is applied last. Output label: [vout].
     *
     * Timing model: scene i's narration ends at cumulative duration end_i.
     * Every scene AFTER the first is stretched by the crossfade overlap and
     * each xfade starts XFADE_SECONDS before the boundary, so scene i+1's
     * content is fully visible exactly when its narration starts and the
     * total video length equals the audio length. Offsets are computed from
     * frame-quantized durations (frames/fps) to avoid cumulative drift.
     */
    static String buildFilterGraph(double[] durations, String assPath, int fps) {
        int n = durations.length;
        // F2 kısa görsel koruması: xfade, en kısa görselin %40'ını aşamaz —
        // aksi halde offset negatife düşüp filtre grafını bozar
        double minDur = java.util.Arrays.stream(durations).min().orElse(XFADE_SECONDS);
        double xfade = Math.min(XFADE_SECONDS, Math.max(0.1, minDur * 0.4));
        long[] frames = new long[n];
        for (int i = 0; i < n; i++) {
            double stretched = durations[i] + (i > 0 ? xfade : 0);
            frames[i] = Math.round(stretched * fps);
        }
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String zoom = (i % 2 == 0)
                    ? "1+0.10*on/" + frames[i]
                    : "1.10-0.10*on/" + frames[i];
            g.append(String.format(Locale.ROOT,
                    "[%d:v]scale=1080:1920:force_original_aspect_ratio=increase,"
                            + "crop=1080:1920,zoompan=z='%s':d=%d:s=1080x1920:fps=%d[v%d];",
                    i, zoom, frames[i], fps, i));
        }
        String prev = "[v0]";
        double chainEnd = (double) frames[0] / fps; // frame-quantized
        for (int i = 1; i < n; i++) {
            double offset = chainEnd - xfade;
            String outLabel = "[x" + i + "]";
            g.append(String.format(Locale.ROOT,
                    "%s[v%d]xfade=transition=fade:duration=%.1f:offset=%.3f%s;",
                    prev, i, xfade, offset, outLabel));
            chainEnd = offset + (double) frames[i] / fps;
            prev = outLabel;
        }
        g.append(prev).append("subtitles=filename='").append(assPath).append("'[vout]");
        return g.toString();
    }

    /**
     * Renders the final video. mixedAudio is the already-mixed
     * voiceover+music track (see AudioProcessor).
     */
    public File render(List<File> sceneImages, double[] sceneDurations,
                       File mixedAudio, File assFile, Path out)
            throws VideoProcessingException {
        if (sceneImages.size() != sceneDurations.length) {
            throw new IllegalArgumentException("images and durations size mismatch: "
                    + sceneImages.size() + " vs " + sceneDurations.length);
        }
        try {
            Files.createDirectories(out.getParent());
        } catch (java.io.IOException e) {
            throw new VideoProcessingException("Cannot create output dir: " + out.getParent(), e);
        }

        List<String> cmd = new ArrayList<>(List.of(ffmpeg.getFfmpegPath()));
        for (File image : sceneImages) {
            // Single-frame image inputs: zoompan expands ONE input frame into
            // exactly d output frames. Looping the input would multiply frames.
            cmd.addAll(List.of("-i", image.getAbsolutePath()));
        }
        cmd.addAll(List.of("-i", mixedAudio.getAbsolutePath(),
                "-filter_complex", buildFilterGraph(sceneDurations, assFile.getPath(), FPS),
                "-map", "[vout]", "-map", sceneImages.size() + ":a",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k",
                // faststart: moov öne — Instagram/Facebook transcoder'ı moov
                // sonda olan mp4'leri reddedebiliyor (canlı bulgu 2026-08-02)
                "-movflags", "+faststart",
                "-shortest", "-y", out.toString()));

        logger.info("Rendering {} scenes -> {}", sceneImages.size(), out.getFileName());
        ffmpeg.executeCommand(cmd, "ken burns render");
        return out.toFile();
    }
}
