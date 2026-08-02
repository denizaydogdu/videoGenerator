package com.videogenerator.processor;

import com.videogenerator.config.Configuration;
import com.videogenerator.util.VideoProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for FFmpeg command-line tool
 */
public class FFmpegWrapper {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegWrapper.class);
    private final Configuration config;
    private final String ffmpegPath;
    private final String ffprobePath;

    public FFmpegWrapper() {
        this.config = Configuration.getInstance();
        this.ffmpegPath = config.getFfmpegPath();
        this.ffprobePath = config.getFfprobePath();
    }

    /**
     * Merges video and audio files
     */
    public File mergeVideoAudio(String videoPath, String audioPath, String outputPath)
            throws VideoProcessingException {
        logger.info("Merging video and audio: {} + {} -> {}", videoPath, audioPath, outputPath);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(videoPath);
        command.add("-i");
        command.add(audioPath);
        command.add("-c:v");
        command.add("copy"); // Copy video codec (no re-encoding)
        command.add("-c:a");
        command.add("aac"); // Encode audio as AAC
        command.add("-shortest"); // End when shortest stream ends
        command.add("-y"); // Overwrite output file
        command.add(outputPath);

        executeCommand(command, "merge video and audio");
        logger.info("Video and audio merged successfully");

        return new File(outputPath);
    }

    /**
     * Creates a video from a static image and audio
     * Useful for music-only content
     */
    public File createVideoFromImageAndAudio(String imagePath, String audioPath, String outputPath)
            throws VideoProcessingException {
        logger.info("Creating video from image and audio: {} + {} -> {}", imagePath, audioPath, outputPath);

        // Get audio duration first
        double audioDuration = getMediaDuration(audioPath);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-loop");
        command.add("1"); // Loop the image
        command.add("-i");
        command.add(imagePath);
        command.add("-i");
        command.add(audioPath);
        command.add("-c:v");
        command.add("libx264");
        command.add("-tune");
        command.add("stillimage");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-vf");
        command.add(String.format("scale=%d:%d", config.getVideoWidth(), config.getVideoHeight()));
        command.add("-shortest");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "create video from image and audio");
        logger.info("Video created successfully from image and audio");

        return new File(outputPath);
    }

    /**
     * Extracts audio duration from media file
     */
    public double getMediaDuration(String mediaPath) throws VideoProcessingException {
        logger.debug("Getting media duration: {}", mediaPath);

        List<String> command = new ArrayList<>();
        command.add(ffprobePath);
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("format=duration");
        command.add("-of");
        command.add("default=noprint_wrappers=1:nokey=1");
        command.add(mediaPath);

        String output = executeCommandWithOutput(command, "get media duration");

        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            throw new VideoProcessingException("Failed to parse media duration: " + output);
        }
    }

    /**
     * Gets video resolution (width x height)
     */
    public String getVideoResolution(String videoPath) throws VideoProcessingException {
        logger.debug("Getting video resolution: {}", videoPath);

        List<String> command = new ArrayList<>();
        command.add(ffprobePath);
        command.add("-v");
        command.add("error");
        command.add("-select_streams");
        command.add("v:0");
        command.add("-show_entries");
        command.add("stream=width,height");
        command.add("-of");
        command.add("csv=s=x:p=0");
        command.add(videoPath);

        return executeCommandWithOutput(command, "get video resolution").trim();
    }

    /**
     * Resizes video to specified dimensions
     */
    public void resizeVideo(String inputPath, String outputPath, int width, int height)
            throws VideoProcessingException {
        logger.info("Resizing video: {} to {}x{}", inputPath, width, height);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-vf");
        command.add(String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2",
                width, height, width, height));
        command.add("-c:a");
        command.add("copy");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "resize video");
        logger.info("Video resized successfully");
    }

    /**
     * Trims video to specified duration
     */
    public void trimVideo(String inputPath, String outputPath, double startTime, double duration)
            throws VideoProcessingException {
        logger.info("Trimming video: {} from {} for {}s", inputPath, startTime, duration);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-ss");
        command.add(String.valueOf(startTime));
        command.add("-t");
        command.add(String.valueOf(duration));
        command.add("-c");
        command.add("copy");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "trim video");
        logger.info("Video trimmed successfully");
    }

    /**
     * Converts video to MP4 format
     */
    public void convertToMp4(String inputPath, String outputPath) throws VideoProcessingException {
        logger.info("Converting to MP4: {} -> {}", inputPath, outputPath);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("medium");
        command.add("-crf");
        command.add("23");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");
        command.add("-movflags");
        command.add("+faststart");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "convert to MP4");
        logger.info("Video converted to MP4 successfully");
    }

    /**
     * Validates that FFmpeg is installed and accessible
     */
    /** Checks the configured ffprobe binary (duration probing depends on it). */
    public boolean validateFfprobeInstallation() {
        try {
            Process process = new ProcessBuilder(ffprobePath, "-version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateFFmpegInstallation() {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-version");

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("FFmpeg is installed and accessible");
                return true;
            } else {
                logger.error("FFmpeg validation failed with exit code: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            logger.error("FFmpeg validation failed", e);
            return false;
        }
    }

    /** Configured ffmpeg binary path (config: ffmpeg.path). */
    public String getFfmpegPath() {
        return ffmpegPath;
    }

    /**
     * Executes FFmpeg command. Public so composite renderers (KenBurns)
     * can reuse the same timeout/error-surfacing behavior.
     */
    public void executeCommand(List<String> command, String operation) throws VideoProcessingException {
        try {
            logger.debug("Executing command: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("FFmpeg: {}", line);
                }
            }

            // Wait for completion with timeout
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroy();
                throw new VideoProcessingException("FFmpeg command timed out after 5 minutes");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Surface only the tail: ffmpeg output can be thousands of lines,
                // the actual error is at the end.
                String[] lines = output.toString().split("\n");
                int from = Math.max(0, lines.length - 20);
                String tail = String.join("\n",
                        java.util.Arrays.copyOfRange(lines, from, lines.length));
                throw new VideoProcessingException(
                        "FFmpeg command failed with exit code " + exitCode
                                + " (last " + (lines.length - from) + " lines):\n" + tail
                );
            }

        } catch (IOException | InterruptedException e) {
            throw new VideoProcessingException("Failed to " + operation + ": " + e.getMessage(), e);
        }
    }

    /**
     * Executes command and returns output
     */
    private String executeCommandWithOutput(List<String> command, String operation) throws VideoProcessingException {
        try {
            logger.debug("Executing command: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new VideoProcessingException(
                        "Command failed with exit code " + exitCode + ": " + output.toString()
                );
            }

            return output.toString();

        } catch (IOException | InterruptedException e) {
            throw new VideoProcessingException("Failed to " + operation + ": " + e.getMessage(), e);
        }
    }

    /**
     * Mixes voiceover and background music with ducking.
     *
     * @param voiceoverPath voiceover audio file path
     * @param musicPath background music file path
     * @param outputPath output file path
     * @param mixConfig audio mix configuration
     * @return output file
     * @throws VideoProcessingException if mixing fails
     */
    /**
     * Ducking miks filtre grafı. Canlı hata sonrası onarıldı (2026-08-02):
     * sidechaincompress'te 1. giriş KISILACAK sinyal (müzik), 2. giriş
     * TETİK sinyaldir (ses) — eski graf tersti ve asplit'in bir çıkışı
     * bağlantısız kaldığı için FFmpeg 8 reddediyordu. Ses hem tetik hem
     * miks girişi olduğu için bölünen SES'tir.
     */
    static String buildMixFilter(com.videogenerator.model.AudioMixConfig mixConfig,
                                 double voiceoverDuration) {
        StringBuilder filter = new StringBuilder();
        filter.append("[0:a]volume=").append(mixConfig.getVoiceoverVolumeString()).append("[voice];");
        filter.append("[1:a]volume=").append(mixConfig.getMusicVolumeString()).append("[music_vol];");

        if (mixConfig.isDuckingEnabled()) {
            filter.append("[voice]asplit[voice_mix][voice_sc];");
            filter.append("[music_vol][voice_sc]sidechaincompress="
                    + "threshold=0.03:ratio=4:attack=5:release=200[music_ducked];");
            filter.append("[voice_mix][music_ducked]amix=inputs=2:duration=first[mixed]");
        } else {
            filter.append("[voice][music_vol]amix=inputs=2:duration=first[mixed]");
        }

        StringBuilder fadeFilter = new StringBuilder();
        if (mixConfig.isFadeInEnabled()) {
            fadeFilter.append("afade=t=in:st=0:d=").append(mixConfig.getFadeInDuration());
        }
        if (mixConfig.isFadeOutEnabled()) {
            if (fadeFilter.length() > 0) {
                fadeFilter.append(",");
            }
            double fadeOutStart = Math.max(0, voiceoverDuration - mixConfig.getFadeOutDuration());
            fadeFilter.append("afade=t=out:st=").append(fadeOutStart)
                    .append(":d=").append(mixConfig.getFadeOutDuration());
        }
        if (fadeFilter.length() > 0) {
            filter.append(";[mixed]").append(fadeFilter).append("[faded]");
        } else {
            filter.append(";[mixed]acopy[faded]");
        }
        if (mixConfig.isNormalizeAudio()) {
            filter.append(";[faded]loudnorm");
        } else {
            filter.append(";[faded]acopy");
        }
        return filter.toString();
    }

    public File mixVoiceoverAndMusic(String voiceoverPath, String musicPath,
                                    String outputPath, com.videogenerator.model.AudioMixConfig mixConfig)
            throws VideoProcessingException {
        logger.info("Mixing voiceover and music with FFmpeg");

        // Get audio duration for fade out calculation
        double voiceoverDuration = getMediaDuration(voiceoverPath);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(voiceoverPath);
        command.add("-i");
        command.add(musicPath);

        command.add("-filter_complex");
        command.add(buildMixFilter(mixConfig, voiceoverDuration));

        // Output settings — -vn: müzik dosyasındaki gömülü kapak resmi
        // (Suno mp3'leri mjpeg akışı taşıyor) çıktıya sızmasın
        command.add("-vn");
        command.add("-codec:a");
        command.add("libmp3lame");
        command.add("-q:a");
        command.add("2");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "mix voiceover and music");

        return new File(outputPath);
    }

    /**
     * Normalizes audio levels.
     *
     * @param inputPath input audio file path
     * @param outputPath output file path
     * @return normalized audio file
     * @throws VideoProcessingException if normalization fails
     */
    public File normalizeAudio(String inputPath, String outputPath) throws VideoProcessingException {
        logger.info("Normalizing audio with FFmpeg");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-filter:a");
        command.add("loudnorm");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "normalize audio");

        return new File(outputPath);
    }

    /**
     * Trims audio to specific duration.
     *
     * @param inputPath input audio file path
     * @param outputPath output file path
     * @param durationSeconds target duration in seconds
     * @return trimmed audio file
     * @throws VideoProcessingException if trimming fails
     */
    public File trimAudio(String inputPath, String outputPath, int durationSeconds)
            throws VideoProcessingException {
        logger.info("Trimming audio to {} seconds", durationSeconds);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-t");
        command.add(String.valueOf(durationSeconds));
        command.add("-acodec");
        command.add("copy");
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "trim audio");

        return new File(outputPath);
    }

    /**
     * Adjusts audio volume.
     *
     * @param inputPath input audio file path
     * @param outputPath output file path
     * @param volumeMultiplier volume multiplier (0.5 = half, 2.0 = double)
     * @return adjusted audio file
     * @throws VideoProcessingException if adjustment fails
     */
    public File adjustVolume(String inputPath, String outputPath, double volumeMultiplier)
            throws VideoProcessingException {
        logger.info("Adjusting audio volume by factor: {}", volumeMultiplier);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputPath);
        command.add("-filter:a");
        command.add("volume=" + volumeMultiplier);
        command.add("-y");
        command.add(outputPath);

        executeCommand(command, "adjust volume");

        return new File(outputPath);
    }
}
