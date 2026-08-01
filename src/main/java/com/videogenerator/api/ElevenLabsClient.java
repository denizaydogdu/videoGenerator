package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.model.VoiceConfig;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.HttpUtil;
import com.videogenerator.util.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for ElevenLabs Text-to-Speech API.
 * Generates natural-sounding voiceovers from text.
 */
public class ElevenLabsClient {
    private static final Logger logger = LoggerFactory.getLogger(ElevenLabsClient.class);
    private final Configuration config;
    private final Gson gson;
    private final String apiKey;

    private static final String API_BASE_URL = "https://api.elevenlabs.io/v1";
    private static final String TTS_ENDPOINT = "/text-to-speech";

    public ElevenLabsClient() {
        this.config = Configuration.getInstance();
        this.gson = new Gson();
        this.apiKey = config.getTtsApiKey();

        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
            logger.warn("ElevenLabs API key is not configured. TTS will not work.");
        }
    }

    /**
     * Generates voiceover audio from text.
     *
     * @param text the text to convert to speech
     * @param voiceConfig voice configuration
     * @param outputFile output file path
     * @return the generated audio file
     * @throws ApiException if generation fails
     */
    public File generateVoiceover(String text, VoiceConfig voiceConfig, File outputFile) throws ApiException {
        logger.info("Generating voiceover with ElevenLabs (voice: {}, {} characters)",
                voiceConfig.getVoiceId(), text.length());

        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
            throw new ApiException(ApiProvider.ELEVENLABS, "ElevenLabs API key is not configured");
        }

        if (!voiceConfig.isValid()) {
            throw new ApiException(ApiProvider.ELEVENLABS, "Invalid voice configuration");
        }

        // Use retry policy for network resilience
        RetryPolicy retryPolicy = new RetryPolicy();

        try {
            return retryPolicy.execute(() -> generateVoiceoverInternal(text, voiceConfig, outputFile),
                    "ElevenLabs TTS");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiProvider.ELEVENLABS, "TTS generation failed after retries", e);
        }
    }

    /**
     * Internal method for voiceover generation (used by retry logic).
     */
    private File generateVoiceoverInternal(String text, VoiceConfig voiceConfig, File outputFile) {
        try {
            // Build request URL
            String url = API_BASE_URL + TTS_ENDPOINT + "/" + voiceConfig.getVoiceId();

            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("text", text);
            requestBody.addProperty("model_id", voiceConfig.getModel());

            // Voice settings
            JsonObject voiceSettings = new JsonObject();
            voiceSettings.addProperty("stability", voiceConfig.getStability());
            voiceSettings.addProperty("similarity_boost", voiceConfig.getSimilarityBoost());
            voiceSettings.addProperty("style", voiceConfig.getStyle());
            voiceSettings.addProperty("use_speaker_boost", voiceConfig.isUseSpeakerBoost());
            requestBody.add("voice_settings", voiceSettings);

            // Headers
            Map<String, String> headers = createHeaders();

            logger.debug("Calling ElevenLabs TTS API: {}", url);

            // Make request
            HttpResponse<byte[]> response = HttpUtil.postBytes(url, gson.toJson(requestBody), headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                String errorBody = new String(response.body());
                logger.error("ElevenLabs API error: {}", errorBody);
                throw new ApiException(
                        ApiProvider.ELEVENLABS,
                        "TTS generation failed: " + errorBody,
                        response.statusCode()
                );
            }

            // Save audio to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(response.body());
            }

            logger.info("Voiceover generated successfully: {} ({} bytes)",
                    outputFile.getName(), response.body().length);

            return outputFile;

        } catch (IOException e) {
            logger.error("Error generating voiceover", e);
            throw new RuntimeException(new ApiException(ApiProvider.ELEVENLABS, "Error generating voiceover", e));
        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw new RuntimeException((ApiException) e);
            }
            throw new RuntimeException(new ApiException(ApiProvider.ELEVENLABS, "Unexpected error in TTS", e));
        }
    }

    /**
     * Generates voiceover with default voice.
     *
     * @param text text to convert
     * @param outputFile output file
     * @return generated audio file
     * @throws ApiException if generation fails
     */
    public File generateVoiceover(String text, File outputFile) throws ApiException {
        VoiceConfig defaultConfig = new VoiceConfig();
        return generateVoiceover(text, defaultConfig, outputFile);
    }

    /**
     * Gets available voices from ElevenLabs API.
     *
     * @return JSON response with available voices
     * @throws ApiException if request fails
     */
    public String getAvailableVoices() throws ApiException {
        logger.info("Fetching available voices from ElevenLabs");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new ApiException(ApiProvider.ELEVENLABS, "API key not configured");
        }

        try {
            String url = API_BASE_URL + "/voices";
            Map<String, String> headers = createHeaders();

            HttpResponse<String> response = HttpUtil.get(url, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.ELEVENLABS,
                        "Failed to fetch voices: " + response.body(),
                        response.statusCode()
                );
            }

            return response.body();

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.ELEVENLABS, "Error fetching voices", e);
        }
    }

    /**
     * Gets the user's subscription info (remaining characters).
     *
     * @return JSON response with subscription data
     * @throws ApiException if request fails
     */
    public String getSubscriptionInfo() throws ApiException {
        logger.info("Fetching subscription info from ElevenLabs");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new ApiException(ApiProvider.ELEVENLABS, "API key not configured");
        }

        try {
            String url = API_BASE_URL + "/user/subscription";
            Map<String, String> headers = createHeaders();

            HttpResponse<String> response = HttpUtil.get(url, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.ELEVENLABS,
                        "Failed to fetch subscription: " + response.body(),
                        response.statusCode()
                );
            }

            return response.body();

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.ELEVENLABS, "Error fetching subscription", e);
        }
    }

    /**
     * Creates HTTP headers with authorization.
     *
     * @return headers map
     */
    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("xi-api-key", apiKey);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "audio/mpeg");
        return headers;
    }

    /**
     * Validates API key by making a test request.
     *
     * @return true if API key is valid
     */
    public boolean validateApiKey() {
        try {
            getSubscriptionInfo();
            return true;
        } catch (Exception e) {
            logger.error("API key validation failed", e);
            return false;
        }
    }

    /**
     * Estimates the cost of generating voiceover.
     * ElevenLabs charges per character.
     *
     * @param text the text
     * @return estimated cost in USD
     */
    public double estimateCost(String text) {
        int characters = text.length();
        // Assuming $0.30 per 1000 characters (standard pricing)
        return (characters / 1000.0) * 0.30;
    }
}
