package com.videogenerator.service;

import com.videogenerator.api.OpenAiGptClient;
import com.videogenerator.api.OpenAiSoraClient;
import com.videogenerator.api.SunoApiClient;
import com.videogenerator.api.YouTubeApiClient;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.*;
import com.videogenerator.processor.VideoProcessor;
import com.videogenerator.util.FileUtil;
import com.videogenerator.util.VideoGeneratorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Main content generation orchestrator
 * Coordinates the entire pipeline: Music → Video → Metadata → Processing → Upload
 */
public class ContentGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(ContentGeneratorService.class);

    private final Configuration config;
    private final SunoApiClient sunoClient;
    private final OpenAiSoraClient soraClient;
    private final OpenAiGptClient gptClient;
    private final YouTubeApiClient youtubeClient;
    private final VideoProcessor videoProcessor;
    private final NicheFinder nicheFinder;
    private final IdeaGenerator ideaGenerator;
    private final ScriptWriter scriptWriter;
    private final TextToSpeechService ttsService;
    private final com.videogenerator.api.KeywordApiClient keywordClient;  // Shared instance to prevent leak

    // Removed shared state for thread safety - each method now manages its own context

    public ContentGeneratorService() throws Exception {
        this.config = Configuration.getInstance();
        this.sunoClient = new SunoApiClient();
        this.soraClient = new OpenAiSoraClient();
        this.gptClient = new OpenAiGptClient();
        this.youtubeClient = new YouTubeApiClient();
        this.videoProcessor = new VideoProcessor();

        // Phase 1 & 2 services - use single KeywordApiClient instance
        this.keywordClient = new com.videogenerator.api.KeywordApiClient();
        TrendAnalyzer trendAnalyzer = new TrendAnalyzer(youtubeClient, keywordClient);
        this.nicheFinder = new NicheFinder(youtubeClient, keywordClient, trendAnalyzer);
        this.ideaGenerator = new IdeaGenerator(gptClient);
        this.scriptWriter = new ScriptWriter(gptClient);
        this.ttsService = new TextToSpeechService();

        logger.info("ContentGeneratorService initialized with TTS support");
    }

    /**
     * Shuts down all resources properly.
     * Call this before application exit to prevent resource leaks.
     */
    public void shutdown() {
        logger.info("Shutting down ContentGeneratorService...");

        // Close KeywordApiClient to release HTTP connections
        if (keywordClient != null) {
            keywordClient.close();
        }

        logger.info("ContentGeneratorService shutdown complete");
    }

    /**
     * Generates and uploads a complete YouTube Short
     * This is the main entry point for the entire pipeline
     */
    public UploadResult generateAndUploadShort() throws VideoGeneratorException {
        logger.info("=== Starting YouTube Shorts generation pipeline ===");

        try {
            // Ensure directories exist
            FileUtil.ensureDirectoryExists(config.getOutputDir());
            FileUtil.ensureDirectoryExists(config.getTempDir());

            // Stage 1: Generate prompts
            logger.info("Stage 1: Generating creative prompts...");

            String musicPrompt = gptClient.generateMusicPrompt();
            logger.info("Music prompt: {}", musicPrompt);

            // Stage 2: Generate music
            logger.info("Stage 2: Generating music...");

            MusicResponse music = sunoClient.generateAndWait(musicPrompt);
            logger.info("Music generated: {} ({}s)", music.getId(), music.getDuration());

            // Download music
            String musicPath = config.getTempDir() + "/" +
                    FileUtil.generateTimestampedFilename("music", "mp3");
            FileUtil.downloadFile(music.getAudioUrl(), musicPath);
            logger.info("Music downloaded to: {}", musicPath);

            // Stage 3: Generate video prompt based on music mood
            String videoPrompt = gptClient.generateVideoPrompt(musicPrompt);
            logger.info("Video prompt: {}", videoPrompt);

            // Stage 4: Generate video
            logger.info("Stage 3: Generating video...");

            VideoResponse video = soraClient.generateAndWait(videoPrompt);
            logger.info("Video generated: {} ({}s)", video.getId(), video.getDuration());

            // Download video
            String rawVideoPath = config.getTempDir() + "/" +
                    FileUtil.generateTimestampedFilename("video", "mp4");
            FileUtil.downloadFile(video.getVideoUrl(), rawVideoPath);
            logger.info("Video downloaded to: {}", rawVideoPath);

            // Stage 5: Process video (merge audio/video, adjust format)
            logger.info("Stage 4: Processing video...");

            String finalVideoFilename = FileUtil.generateTimestampedFilename("short", "mp4");
            File finalVideo = videoProcessor.processVideoForShorts(
                    rawVideoPath,
                    musicPath,
                    finalVideoFilename
            );

            // Validate final video
            if (!videoProcessor.validateShortsRequirements(finalVideo.getAbsolutePath())) {
                throw new VideoGeneratorException("Generated video does not meet YouTube Shorts requirements");
            }

            // Stage 6: Generate metadata
            logger.info("Stage 5: Generating metadata...");

            VideoMetadata metadata = gptClient.generateMetadata(musicPrompt, videoPrompt);
            logger.info("Metadata generated: {}", metadata);

            // Stage 7: Upload to YouTube
            logger.info("Stage 6: Uploading to YouTube...");

            UploadResult result = youtubeClient.uploadVideo(finalVideo, metadata);
            logger.info("Video uploaded successfully: {}", result.getUrl());

            // Stage 8: Cleanup
            logger.info("Stage 7: Cleaning up temporary files...");

            cleanupTempFiles(musicPath, rawVideoPath);

            // Final stage

            logger.info("=== YouTube Shorts generation completed successfully ===");
            logger.info("Video ID: {}", result.getVideoId());
            logger.info("URL: {}", result.getUrl());
            logger.info("Shorts URL: {}", result.getShortsUrl());

            return result;

        } catch (Exception e) {
            logger.error("Error in content generation pipeline", e);
            throw new VideoGeneratorException("Content generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Alternative method: Generate video with static image instead of AI video
     * This is more cost-effective and faster
     */
    public UploadResult generateWithStaticImage(String imagePath) throws VideoGeneratorException {
        logger.info("=== Starting YouTube Shorts generation with static image ===");

        try {
            FileUtil.ensureDirectoryExists(config.getOutputDir());
            FileUtil.ensureDirectoryExists(config.getTempDir());

            // Validate image exists
            if (!FileUtil.isValidFile(imagePath)) {
                throw new VideoGeneratorException("Image file not found: " + imagePath);
            }

            // Stage 1: Generate music prompt
            logger.info("Stage 1: Generating music prompt...");

            String musicPrompt = gptClient.generateMusicPrompt();
            logger.info("Music prompt: {}", musicPrompt);

            // Stage 2: Generate music
            logger.info("Stage 2: Generating music...");

            MusicResponse music = sunoClient.generateAndWait(musicPrompt);
            String musicPath = config.getTempDir() + "/" +
                    FileUtil.generateTimestampedFilename("music", "mp3");
            FileUtil.downloadFile(music.getAudioUrl(), musicPath);

            // Stage 3: Create video from image and music
            logger.info("Stage 3: Creating video from image and music...");

            String finalVideoFilename = FileUtil.generateTimestampedFilename("short", "mp4");
            File finalVideo = videoProcessor.createVideoFromImageAndAudio(
                    imagePath,
                    musicPath,
                    finalVideoFilename
            );

            // Stage 4: Generate metadata
            logger.info("Stage 4: Generating metadata...");

            VideoMetadata metadata = gptClient.generateMetadata(musicPrompt, "Abstract visual with music");

            // Stage 5: Upload
            logger.info("Stage 5: Uploading to YouTube...");

            UploadResult result = youtubeClient.uploadVideo(finalVideo, metadata);

            // Cleanup
            cleanupTempFiles(musicPath);


            logger.info("=== YouTube Shorts generation completed successfully ===");
            logger.info("Shorts URL: {}", result.getShortsUrl());

            return result;

        } catch (Exception e) {
            logger.error("Error in content generation pipeline", e);
            throw new VideoGeneratorException("Content generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cleans up temporary files
     */
    private void cleanupTempFiles(String... filePaths) {
        for (String filePath : filePaths) {
            try {
                FileUtil.deleteFile(filePath);
            } catch (Exception e) {
                logger.warn("Failed to delete temp file: {}", filePath, e);
            }
        }
    }

    // Thread-safe: No shared state - removed updateStage/updateStatus methods
    // Each pipeline execution is now stateless and thread-safe

    /**
     * Validates all required services are available
     */
    public boolean validateServices() {
        logger.info("Validating all services...");

        boolean allValid = true;

        // Validate FFmpeg
        if (!videoProcessor.validateFFmpegInstallation()) {
            logger.error("FFmpeg validation failed");
            allValid = false;
        }

        // Validate API keys
        if (!sunoClient.validateApiKey()) {
            logger.error("Suno API key validation failed");
            allValid = false;
        }

        if (!soraClient.validateApiKey()) {
            logger.error("OpenAI Sora API key validation failed");
            allValid = false;
        }

        if (!gptClient.validateApiKey()) {
            logger.error("OpenAI GPT API key validation failed");
            allValid = false;
        }

        if (!youtubeClient.testConnection()) {
            logger.error("YouTube API connection test failed");
            allValid = false;
        }

        // Validate TTS (optional, only if enabled)
        if (config.isTtsEnabled()) {
            if (!ttsService.isTtsConfigured()) {
                logger.warn("TTS is enabled but not configured properly");
                // Don't mark as failure since TTS is optional
            }
        }

        if (allValid) {
            logger.info("All services validated successfully");
        } else {
            logger.error("Some services failed validation");
        }

        return allValid;
    }

    /**
     * Generates and uploads a complete YouTube Short with TTS voiceover.
     * Uses the full Phase 1 & 2 pipeline: Niche → Idea → Script → TTS → Music → Video → Upload
     *
     * @return upload result
     * @throws VideoGeneratorException if generation fails
     */
    public UploadResult generateAndUploadShortWithTTS() throws VideoGeneratorException {
        logger.info("=== Starting FULL AI Pipeline (Niche → TTS → Video → Upload) ===");

        try {
            FileUtil.ensureDirectoryExists(config.getOutputDir());
            FileUtil.ensureDirectoryExists(config.getTempDir());

            // ========== PHASE 1: NICHE & IDEA GENERATION ==========

            // Stage 1: Find trending niche
            logger.info("Stage 1: Discovering trending niche...");

            NicheData selectedNiche = null;
            if (config.isNicheFinderEnabled()) {
                List<NicheData> topNiches = nicheFinder.findTopNiches(
                    config.getNicheDiscoveryCount(),
                    config.getNicheTrendRegion()
                );

                if (!topNiches.isEmpty()) {
                    selectedNiche = topNiches.get(0);
                    logger.info("Selected niche: {} (score: {:.2f})",
                        selectedNiche.getTopic(), selectedNiche.getOverallScore());
                }
            }

            // Stage 2: Generate viral content ideas
            logger.info("Stage 2: Generating viral content ideas...");

            ContentIdea selectedIdea;
            if (selectedNiche != null) {
                List<ContentIdea> ideas = ideaGenerator.generateIdeas(
                    selectedNiche,
                    config.getIdeaGeneratorCount()
                );
                selectedIdea = ideaGenerator.selectBestIdea(ideas);
            } else {
                // Fallback: use random idea or generate generic
                selectedIdea = ideaGenerator.getRandomIdea();
                if (selectedIdea == null) {
                    logger.warn("No ideas available, using fallback prompt generation");
                    return generateAndUploadShort(); // Fallback to original pipeline
                }
            }

            logger.info("Selected idea: {} (CTR: {:.2f})",
                selectedIdea.getTitle(), selectedIdea.getEstimatedCtr());

            // ========== PHASE 2: SCRIPT & VOICEOVER GENERATION ==========

            // Stage 3: Generate voiceover script
            logger.info("Stage 3: Generating voiceover script...");

            VoiceoverScript script = scriptWriter.generateScript(selectedIdea);
            logger.info("Script generated: {} words, ~{} seconds",
                script.getEstimatedWordCount(), script.getEstimatedDuration());

            // Stage 4: Generate voiceover with TTS
            logger.info("Stage 4: Generating voiceover with TTS...");

            String voiceoverFilename = FileUtil.generateTimestampedFilename("voiceover", "mp3");
            File voiceoverFile = ttsService.generateVoiceover(script, voiceoverFilename);
            logger.info("Voiceover generated: {}", voiceoverFile.getName());

            // ========== ORIGINAL PIPELINE: MUSIC & VIDEO ==========

            // Stage 5: Generate music prompt
            logger.info("Stage 5: Generating music prompt...");
            String musicPrompt = "Upbeat background music matching theme: " + selectedIdea.getTitle();

            // Stage 6: Generate music
            logger.info("Stage 6: Generating background music...");

            MusicResponse music = sunoClient.generateAndWait(musicPrompt);
            String musicPath = config.getTempDir() + "/" +
                    FileUtil.generateTimestampedFilename("music", "mp3");
            FileUtil.downloadFile(music.getAudioUrl(), musicPath);
            logger.info("Music downloaded: {}", musicPath);

            // Stage 7: Generate video prompt
            logger.info("Stage 7: Generating video prompt...");
            String videoPrompt = "Visual representation: " + selectedIdea.getDescription();

            // Stage 8: Generate video
            logger.info("Stage 8: Generating video with Sora...");

            VideoResponse video = soraClient.generateAndWait(videoPrompt);
            String rawVideoPath = config.getTempDir() + "/" +
                    FileUtil.generateTimestampedFilename("video", "mp4");
            FileUtil.downloadFile(video.getVideoUrl(), rawVideoPath);
            logger.info("Video downloaded: {}", rawVideoPath);

            // ========== AUDIO MIXING & VIDEO PROCESSING ==========

            // Stage 9: Mix voiceover + background music, then merge with video
            logger.info("Stage 9: Mixing audio and processing video...");

            String finalVideoFilename = FileUtil.generateTimestampedFilename("short", "mp4");
            String finalVideoPath = config.getOutputDir() + "/" + finalVideoFilename;

            File finalVideo = videoProcessor.processVideoWithVoiceover(
                rawVideoPath,
                voiceoverFile.getAbsolutePath(),
                musicPath,
                finalVideoPath
            );

            // Validate
            if (!videoProcessor.validateShortsRequirements(finalVideo.getAbsolutePath())) {
                throw new VideoGeneratorException("Video does not meet Shorts requirements");
            }

            // Stage 10: Generate metadata
            logger.info("Stage 10: Generating metadata...");

            VideoMetadata metadata = new VideoMetadata();
            metadata.setTitle(selectedIdea.getTitle());
            metadata.setDescription(selectedIdea.getFullDescription());
            metadata.setHashtags(selectedIdea.getHashtags());
            logger.info("Using idea-based metadata: {}", metadata.getTitle());

            // Stage 11: Upload to YouTube
            logger.info("Stage 11: Uploading to YouTube...");

            UploadResult result = youtubeClient.uploadVideo(finalVideo, metadata);

            // Stage 12: Cleanup
            logger.info("Stage 12: Cleaning up...");

            cleanupTempFiles(musicPath, rawVideoPath, voiceoverFile.getAbsolutePath());


            logger.info("=== FULL AI Pipeline completed successfully ===");
            logger.info("Niche: {}", selectedNiche != null ? selectedNiche.getTopic() : "N/A");
            logger.info("Title: {}", selectedIdea.getTitle());
            logger.info("Video ID: {}", result.getVideoId());
            logger.info("URL: {}", result.getShortsUrl());

            return result;

        } catch (Exception e) {
            logger.error("Error in TTS-enabled pipeline", e);
            throw new VideoGeneratorException("TTS pipeline failed: " + e.getMessage(), e);
        }
    }
}
