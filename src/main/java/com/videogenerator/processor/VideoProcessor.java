package com.videogenerator.processor;

import com.videogenerator.config.Configuration;
import com.videogenerator.util.FileUtil;
import com.videogenerator.util.VideoProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * High-level video processing service
 * Coordinates FFmpeg operations for video generation
 */
public class VideoProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessor.class);
    private final Configuration config;
    private final FFmpegWrapper ffmpeg;

    public VideoProcessor() {
        this.config = Configuration.getInstance();
        this.ffmpeg = new FFmpegWrapper();
    }

    /**
     * Processes and combines video and audio into final YouTube Shorts format
     */
    public File processVideoForShorts(String videoPath, String audioPath, String outputFilename)
            throws VideoProcessingException {
        logger.info("Processing video for Shorts: video={}, audio={}", videoPath, audioPath);

        try {
            // Ensure output directory exists
            String outputDir = config.getOutputDir();
            FileUtil.ensureDirectoryExists(outputDir);

            String outputPath = outputDir + "/" + outputFilename;

            // Step 1: Check video duration and audio duration
            double videoDuration = ffmpeg.getMediaDuration(videoPath);
            double audioDuration = ffmpeg.getMediaDuration(audioPath);

            logger.info("Video duration: {}s, Audio duration: {}s", videoDuration, audioDuration);

            // Step 2: Validate duration (must be < 60 seconds for Shorts)
            double finalDuration = Math.min(videoDuration, audioDuration);
            if (finalDuration > config.getMaxVideoDuration()) {
                logger.warn("Video duration exceeds maximum: {}s > {}s, trimming...",
                        finalDuration, config.getMaxVideoDuration());

                // Trim video if needed
                String tempVideoPath = config.getTempDir() + "/trimmed_video.mp4";
                ffmpeg.trimVideo(videoPath, tempVideoPath, 0, config.getMaxVideoDuration());
                videoPath = tempVideoPath;

                // Trim audio if needed
                String tempAudioPath = config.getTempDir() + "/trimmed_audio.mp3";
                ffmpeg.trimVideo(audioPath, tempAudioPath, 0, config.getMaxVideoDuration());
                audioPath = tempAudioPath;
            }

            // Step 3: Check and adjust video resolution
            String resolution = ffmpeg.getVideoResolution(videoPath);
            logger.info("Video resolution: {}", resolution);

            String[] dimensions = resolution.split("x");
            int currentWidth = Integer.parseInt(dimensions[0]);
            int currentHeight = Integer.parseInt(dimensions[1]);

            int targetWidth = config.getVideoWidth();
            int targetHeight = config.getVideoHeight();

            // If resolution doesn't match, resize
            if (currentWidth != targetWidth || currentHeight != targetHeight) {
                logger.info("Resizing video from {}x{} to {}x{}",
                        currentWidth, currentHeight, targetWidth, targetHeight);

                String tempResizedPath = config.getTempDir() + "/resized_video.mp4";
                ffmpeg.resizeVideo(videoPath, tempResizedPath, targetWidth, targetHeight);
                videoPath = tempResizedPath;
            }

            // Step 4: Merge video and audio
            ffmpeg.mergeVideoAudio(videoPath, audioPath, outputPath);

            File outputFile = new File(outputPath);
            logger.info("Video processed successfully: {} ({})",
                    outputFile.getName(), FileUtil.getFileSizeFormatted(outputPath));

            return outputFile;

        } catch (Exception e) {
            logger.error("Error processing video", e);
            throw new VideoProcessingException("Failed to process video for Shorts", e);
        }
    }

    /**
     * Creates a video from static image and audio (for music-only content)
     */
    public File createVideoFromImageAndAudio(String imagePath, String audioPath, String outputFilename)
            throws VideoProcessingException {
        logger.info("Creating video from image and audio: image={}, audio={}", imagePath, audioPath);

        try {
            // Ensure directories exist
            FileUtil.ensureDirectoryExists(config.getOutputDir());
            FileUtil.ensureDirectoryExists(config.getTempDir());

            String outputPath = config.getOutputDir() + "/" + outputFilename;

            // Check audio duration
            double audioDuration = ffmpeg.getMediaDuration(audioPath);
            logger.info("Audio duration: {}s", audioDuration);

            // If audio is too long, trim it
            if (audioDuration > config.getMaxVideoDuration()) {
                logger.warn("Audio too long, trimming to {}s", config.getMaxVideoDuration());
                String tempAudioPath = config.getTempDir() + "/trimmed_audio.mp3";
                ffmpeg.trimVideo(audioPath, tempAudioPath, 0, config.getMaxVideoDuration());
                audioPath = tempAudioPath;
            }

            // Create video
            ffmpeg.createVideoFromImageAndAudio(imagePath, audioPath, outputPath);

            File outputFile = new File(outputPath);
            logger.info("Video created successfully: {} ({})",
                    outputFile.getName(), FileUtil.getFileSizeFormatted(outputPath));

            return outputFile;

        } catch (Exception e) {
            logger.error("Error creating video from image", e);
            throw new VideoProcessingException("Failed to create video from image and audio", e);
        }
    }

    /**
     * Validates video meets YouTube Shorts requirements
     */
    public boolean validateShortsRequirements(String videoPath) {
        try {
            // Check duration
            double duration = ffmpeg.getMediaDuration(videoPath);
            if (duration > config.getMaxVideoDuration()) {
                logger.error("Video duration {} exceeds maximum {}", duration, config.getMaxVideoDuration());
                return false;
            }

            // Check resolution
            String resolution = ffmpeg.getVideoResolution(videoPath);
            String[] dimensions = resolution.split("x");
            int width = Integer.parseInt(dimensions[0]);
            int height = Integer.parseInt(dimensions[1]);

            int targetWidth = config.getVideoWidth();
            int targetHeight = config.getVideoHeight();

            if (width != targetWidth || height != targetHeight) {
                logger.error("Video resolution {}x{} does not match target {}x{}",
                        width, height, targetWidth, targetHeight);
                return false;
            }

            // Check file exists and has size
            File videoFile = new File(videoPath);
            if (!videoFile.exists() || videoFile.length() == 0) {
                logger.error("Video file does not exist or is empty");
                return false;
            }

            logger.info("Video meets YouTube Shorts requirements");
            return true;

        } catch (Exception e) {
            logger.error("Error validating video", e);
            return false;
        }
    }

    /**
     * Validates FFmpeg installation
     */
    public boolean validateFFmpegInstallation() {
        return ffmpeg.validateFFmpegInstallation();
    }

    /**
     * Processes video with voiceover and background music.
     * Mixes voiceover + music, then combines with video.
     *
     * @param videoPath video file path
     * @param voiceoverPath voiceover audio file path
     * @param musicPath background music file path
     * @param outputPath output file path
     * @return processed video file
     * @throws Exception if processing fails
     */
    public File processVideoWithVoiceover(String videoPath, String voiceoverPath,
                                         String musicPath, String outputPath) throws Exception {
        logger.info("Processing video with voiceover and music");

        // Step 1: Mix voiceover and background music
        AudioProcessor audioProcessor = new AudioProcessor();
        String mixedAudioPath = outputPath.replace(".mp4", "_audio_mix.mp3");
        File mixedAudio = audioProcessor.mixVoiceoverAndMusic(
                new File(voiceoverPath),
                new File(musicPath),
                "temp_audio_mix"
        );

        // Step 2: Merge mixed audio with video
        File finalVideo = ffmpeg.mergeVideoAudio(videoPath, mixedAudio.getAbsolutePath(), outputPath);

        // Cleanup temp files
        if (mixedAudio.exists() && !mixedAudio.delete()) {
            logger.warn("Failed to delete temp audio file: {}", mixedAudio);
        }

        logger.info("Video processed with voiceover successfully: {}", finalVideo.getName());
        return finalVideo;
    }

    /**
     * Creates a video from image with voiceover and background music.
     * Alternative to Sora when using static images.
     *
     * @param imagePath image file path
     * @param voiceoverPath voiceover audio file path
     * @param musicPath background music file path
     * @param outputPath output file path
     * @return created video file
     * @throws Exception if creation fails
     */
    public File createVideoWithVoiceover(String imagePath, String voiceoverPath,
                                        String musicPath, String outputPath) throws Exception {
        logger.info("Creating video from image with voiceover and music");

        // Step 1: Mix voiceover and background music
        AudioProcessor audioProcessor = new AudioProcessor();
        File mixedAudio = audioProcessor.mixVoiceoverAndMusic(
                new File(voiceoverPath),
                new File(musicPath),
                "temp_audio_mix"
        );

        // Step 2: Create video from image with mixed audio (duration auto-detected from audio)
        File video = ffmpeg.createVideoFromImageAndAudio(
                imagePath,
                mixedAudio.getAbsolutePath(),
                outputPath
        );

        // Cleanup temp files
        if (mixedAudio.exists() && !mixedAudio.delete()) {
            logger.warn("Failed to delete temp audio file: {}", mixedAudio);
        }

        logger.info("Video created with voiceover successfully: {}", video.getName());
        return video;
    }
}
