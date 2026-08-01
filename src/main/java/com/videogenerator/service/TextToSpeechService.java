package com.videogenerator.service;

import com.videogenerator.api.ElevenLabsClient;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.VoiceConfig;
import com.videogenerator.model.VoiceoverScript;
import com.videogenerator.util.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * Service for managing text-to-speech operations.
 * Orchestrates script generation and voiceover audio creation.
 */
public class TextToSpeechService {
    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechService.class);
    private final ElevenLabsClient ttsClient;
    private final Configuration config;
    private final String outputDir;

    public TextToSpeechService() {
        this.ttsClient = new ElevenLabsClient();
        this.config = Configuration.getInstance();
        this.outputDir = "temp";
    }

    /**
     * Generates voiceover audio from a script.
     *
     * @param script the voiceover script
     * @param outputFileName output file name (without extension)
     * @return the generated audio file
     * @throws ApiException if generation fails
     */
    public File generateVoiceover(VoiceoverScript script, String outputFileName) throws ApiException {
        logger.info("Generating voiceover for script: {} words, {} seconds estimated",
                script.getEstimatedWordCount(), script.getEstimatedDuration());

        if (!config.isTtsEnabled()) {
            logger.warn("TTS is disabled in configuration");
            throw new ApiException(null, "TTS is disabled");
        }

        // Validate script
        if (!script.isValid()) {
            throw new ApiException(null, "Invalid script for voiceover generation");
        }

        // Create voice config from configuration
        VoiceConfig voiceConfig = createVoiceConfigFromSettings();

        // Get clean script text (without pause markers)
        String scriptText = script.getCleanScript();

        // Output file path
        File outputFile = Paths.get(outputDir, outputFileName + ".mp3").toFile();

        // Ensure temp directory exists
        outputFile.getParentFile().mkdirs();

        // Generate voiceover
        File audioFile = ttsClient.generateVoiceover(scriptText, voiceConfig, outputFile);

        logger.info("Voiceover generated successfully: {}", audioFile.getName());

        return audioFile;
    }

    /**
     * Generates voiceover with custom voice config.
     *
     * @param script voiceover script
     * @param voiceConfig custom voice configuration
     * @param outputFileName output file name
     * @return generated audio file
     * @throws ApiException if generation fails
     */
    public File generateVoiceover(VoiceoverScript script, VoiceConfig voiceConfig, String outputFileName)
            throws ApiException {
        logger.info("Generating voiceover with custom voice config");

        if (!script.isValid()) {
            throw new ApiException(null, "Invalid script");
        }

        String scriptText = script.getCleanScript();
        File outputFile = Paths.get(outputDir, outputFileName + ".mp3").toFile();
        outputFile.getParentFile().mkdirs();

        return ttsClient.generateVoiceover(scriptText, voiceConfig, outputFile);
    }

    /**
     * Creates a VoiceConfig from application settings.
     *
     * @return voice configuration
     */
    private VoiceConfig createVoiceConfigFromSettings() {
        VoiceConfig config = new VoiceConfig();
        config.setVoiceId(this.config.getTtsVoiceId());
        config.setModel(this.config.getTtsModel());
        config.setStability(this.config.getTtsStability());
        config.setSimilarityBoost(this.config.getTtsSimilarityBoost());
        config.setUseSpeakerBoost(true);

        logger.debug("Created voice config from settings: {}", config);
        return config;
    }

    /**
     * Estimates the cost of generating voiceover for a script.
     *
     * @param script the script
     * @return estimated cost in USD
     */
    public double estimateCost(VoiceoverScript script) {
        String text = script.getCleanScript();
        return ttsClient.estimateCost(text);
    }

    /**
     * Validates that TTS is properly configured.
     *
     * @return true if TTS is ready to use
     */
    public boolean isTtsConfigured() {
        if (!config.isTtsEnabled()) {
            logger.warn("TTS is disabled");
            return false;
        }

        String apiKey = config.getTtsApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
            logger.warn("TTS API key is not configured");
            return false;
        }

        return true;
    }

    /**
     * Tests the TTS service by validating API key.
     *
     * @return true if service is working
     */
    public boolean testConnection() {
        try {
            return ttsClient.validateApiKey();
        } catch (Exception e) {
            logger.error("TTS connection test failed", e);
            return false;
        }
    }

    /**
     * Gets available voices from TTS provider.
     *
     * @return JSON response with available voices
     * @throws ApiException if request fails
     */
    public String getAvailableVoices() throws ApiException {
        return ttsClient.getAvailableVoices();
    }

    /**
     * Gets subscription info (remaining characters, etc.).
     *
     * @return JSON response with subscription data
     * @throws ApiException if request fails
     */
    public String getSubscriptionInfo() throws ApiException {
        return ttsClient.getSubscriptionInfo();
    }
}
