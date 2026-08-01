package com.videogenerator.processor;

import com.videogenerator.config.Configuration;
import com.videogenerator.model.AudioMixConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * Processes and mixes audio files.
 * Handles voiceover + background music mixing with ducking and normalization.
 */
public class AudioProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AudioProcessor.class);
    private final FFmpegWrapper ffmpegWrapper;
    private final Configuration config;
    private final String outputDir;

    public AudioProcessor() {
        this.ffmpegWrapper = new FFmpegWrapper();
        this.config = Configuration.getInstance();
        this.outputDir = "temp";
    }

    /**
     * Mixes voiceover and background music.
     *
     * @param voiceoverFile voiceover audio file
     * @param musicFile background music file
     * @param outputFileName output file name (without extension)
     * @return mixed audio file
     * @throws Exception if mixing fails
     */
    public File mixVoiceoverAndMusic(File voiceoverFile, File musicFile, String outputFileName) throws Exception {
        logger.info("Mixing voiceover and music: {} + {}",
                voiceoverFile.getName(), musicFile.getName());

        // Create mix config from settings
        AudioMixConfig mixConfig = createMixConfigFromSettings();

        return mixVoiceoverAndMusic(voiceoverFile, musicFile, mixConfig, outputFileName);
    }

    /**
     * Mixes voiceover and background music with custom configuration.
     *
     * @param voiceoverFile voiceover audio file
     * @param musicFile background music file
     * @param mixConfig audio mix configuration
     * @param outputFileName output file name
     * @return mixed audio file
     * @throws Exception if mixing fails
     */
    public File mixVoiceoverAndMusic(File voiceoverFile, File musicFile,
                                    AudioMixConfig mixConfig, String outputFileName) throws Exception {
        logger.info("Mixing audio with config: {}", mixConfig);

        if (!mixConfig.isValid()) {
            throw new IllegalArgumentException("Invalid audio mix configuration");
        }

        // Validate input files
        if (!voiceoverFile.exists()) {
            throw new IllegalArgumentException("Voiceover file does not exist: " + voiceoverFile);
        }
        if (!musicFile.exists()) {
            throw new IllegalArgumentException("Music file does not exist: " + musicFile);
        }

        // Output file path
        File outputFile = Paths.get(outputDir, outputFileName + "_mixed.mp3").toFile();
        outputFile.getParentFile().mkdirs();

        // Delegate to FFmpegWrapper
        File mixedFile = ffmpegWrapper.mixVoiceoverAndMusic(
                voiceoverFile.getAbsolutePath(),
                musicFile.getAbsolutePath(),
                outputFile.getAbsolutePath(),
                mixConfig
        );

        logger.info("Audio mixed successfully: {}", mixedFile.getName());

        return mixedFile;
    }

    /**
     * Normalizes audio levels in a file.
     *
     * @param audioFile audio file to normalize
     * @param outputFileName output file name
     * @return normalized audio file
     * @throws Exception if normalization fails
     */
    public File normalizeAudio(File audioFile, String outputFileName) throws Exception {
        logger.info("Normalizing audio: {}", audioFile.getName());

        File outputFile = Paths.get(outputDir, outputFileName + "_normalized.mp3").toFile();
        outputFile.getParentFile().mkdirs();

        File normalizedFile = ffmpegWrapper.normalizeAudio(
                audioFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
        );

        logger.info("Audio normalized: {}", normalizedFile.getName());
        return normalizedFile;
    }

    /**
     * Trims audio to a specific duration.
     *
     * @param audioFile audio file to trim
     * @param durationSeconds target duration in seconds
     * @param outputFileName output file name
     * @return trimmed audio file
     * @throws Exception if trimming fails
     */
    public File trimAudio(File audioFile, int durationSeconds, String outputFileName) throws Exception {
        logger.info("Trimming audio to {} seconds", durationSeconds);

        File outputFile = Paths.get(outputDir, outputFileName + "_trimmed.mp3").toFile();
        outputFile.getParentFile().mkdirs();

        File trimmedFile = ffmpegWrapper.trimAudio(
                audioFile.getAbsolutePath(),
                outputFile.getAbsolutePath(),
                durationSeconds
        );

        logger.info("Audio trimmed: {}", trimmedFile.getName());
        return trimmedFile;
    }

    /**
     * Adjusts audio volume.
     *
     * @param audioFile audio file
     * @param volumeMultiplier volume multiplier (0.5 = half, 2.0 = double)
     * @param outputFileName output file name
     * @return adjusted audio file
     * @throws Exception if adjustment fails
     */
    public File adjustVolume(File audioFile, double volumeMultiplier, String outputFileName) throws Exception {
        logger.info("Adjusting audio volume by factor: {}", volumeMultiplier);

        File outputFile = Paths.get(outputDir, outputFileName + "_adjusted.mp3").toFile();
        outputFile.getParentFile().mkdirs();

        File adjustedFile = ffmpegWrapper.adjustVolume(
                audioFile.getAbsolutePath(),
                outputFile.getAbsolutePath(),
                volumeMultiplier
        );

        logger.info("Audio volume adjusted: {}", adjustedFile.getName());
        return adjustedFile;
    }

    /**
     * Creates an AudioMixConfig from application settings.
     *
     * @return audio mix configuration
     */
    private AudioMixConfig createMixConfigFromSettings() {
        AudioMixConfig config = new AudioMixConfig();
        config.setVoiceoverVolume(this.config.getAudioVoiceoverVolume());
        config.setMusicVolume(this.config.getAudioMusicVolume());
        config.setDuckingEnabled(this.config.isAudioDuckingEnabled());
        config.setDuckingAmount(this.config.getAudioDuckingAmount());
        config.setNormalizeAudio(this.config.isAudioNormalize());
        config.setFadeInEnabled(true);
        config.setFadeOutEnabled(true);
        config.setFadeInDuration(0.5);
        config.setFadeOutDuration(1.0);

        logger.debug("Created mix config from settings: {}", config);
        return config;
    }

    /**
     * Gets the duration of an audio file in seconds.
     *
     * @param audioFile audio file
     * @return duration in seconds
     * @throws Exception if reading duration fails
     */
    public int getAudioDuration(File audioFile) throws Exception {
        return (int) ffmpegWrapper.getMediaDuration(audioFile.getAbsolutePath());
    }

    /**
     * Validates that FFmpeg is properly installed and configured.
     *
     * @return true if FFmpeg is available
     */
    public boolean validateFFmpeg() {
        return ffmpegWrapper.validateFFmpegInstallation();
    }
}
