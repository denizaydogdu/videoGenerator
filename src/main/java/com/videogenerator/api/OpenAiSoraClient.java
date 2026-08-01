package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.model.VideoRequest;
import com.videogenerator.model.VideoResponse;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import com.videogenerator.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for OpenAI Sora video generation API
 * Note: Sora API is currently in limited access. This implementation is based on
 * anticipated API structure and will need updates when the official API is fully released.
 */
public class OpenAiSoraClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiSoraClient.class);
    private final Configuration config;
    private final Gson gson;
    private final String apiKey;

    // Note: Actual Sora endpoint may differ when officially released
    private static final String SORA_ENDPOINT = "/video/generations";
    private static final int MAX_POLL_ATTEMPTS = 60; // 5 minutes with 5-second intervals
    private static final int POLL_INTERVAL_MS = 5000;

    public OpenAiSoraClient() {
        this.config = Configuration.getInstance();
        this.gson = new Gson();
        this.apiKey = config.getOpenAiApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
    }

    /**
     * Generates a video from a text prompt
     * This is an async operation - returns a task ID that needs to be polled
     */
    public String generateVideo(String prompt) throws ApiException {
        logger.info("Generating video with prompt: {}", prompt);

        try {
            VideoRequest request = new VideoRequest(prompt);
            request.setDuration(15); // 15 seconds for Shorts
            request.setAspectRatio("9:16"); // Vertical format

            String requestBody = gson.toJson(request);
            Map<String, String> headers = createHeaders();
            String url = Constants.OPENAI_API_BASE_URL + SORA_ENDPOINT;

            logger.debug("Sora API request: {}", requestBody);

            HttpResponse<String> response = HttpUtil.post(url, requestBody, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                logger.error("Sora API error response: {}", response.body());
                throw new ApiException(
                        ApiProvider.OPENAI_SORA,
                        "Failed to generate video: " + response.body(),
                        response.statusCode()
                );
            }

            // Parse response to get task/generation ID
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String taskId = responseJson.get("id").getAsString();

            logger.info("Video generation initiated with ID: {}", taskId);
            return taskId;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.OPENAI_SORA, "Error generating video", e);
        }
    }

    /**
     * Queries the status of a video generation task
     */
    public VideoResponse queryVideoStatus(String taskId) throws ApiException {
        logger.debug("Querying video status for task: {}", taskId);

        try {
            String url = Constants.OPENAI_API_BASE_URL + SORA_ENDPOINT + "/" + taskId;
            Map<String, String> headers = createHeaders();

            HttpResponse<String> response = HttpUtil.get(url, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.OPENAI_SORA,
                        "Failed to query video status: " + response.body(),
                        response.statusCode()
                );
            }

            VideoResponse videoResponse = gson.fromJson(response.body(), VideoResponse.class);
            logger.debug("Video status: {} for task: {}", videoResponse.getStatus(), taskId);

            return videoResponse;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.OPENAI_SORA, "Error querying video status", e);
        }
    }

    /**
     * Generates a video and waits until it's ready (with polling)
     */
    public VideoResponse generateAndWait(String prompt) throws ApiException, InterruptedException {
        logger.info("Generating video and waiting for completion...");

        // Step 1: Initiate generation
        String taskId = generateVideo(prompt);

        // Step 2: Poll for completion
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            VideoResponse response = queryVideoStatus(taskId);

            if (response.isCompleted() && response.getVideoUrl() != null) {
                logger.info("Video generation completed: {} (duration: {}s, resolution: {}x{})",
                        response.getId(), response.getDuration(), response.getWidth(), response.getHeight());
                return response;
            }

            if (response.isFailed()) {
                throw new ApiException(
                        ApiProvider.OPENAI_SORA,
                        "Video generation failed for task: " + taskId
                );
            }

            // Wait before next poll
            logger.debug("Video still processing (attempt {}/{}), waiting {}ms...",
                    attempt, MAX_POLL_ATTEMPTS, POLL_INTERVAL_MS);
            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new ApiException(
                ApiProvider.OPENAI_SORA,
                "Video generation timeout after " + (MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000) + " seconds"
        );
    }

    /**
     * Alternative method for direct video generation (if API supports synchronous mode)
     * This is a fallback method in case Sora API provides direct video generation
     */
    public String generateVideoDirectly(String prompt) throws ApiException {
        logger.info("Attempting direct video generation with prompt: {}", prompt);

        try {
            VideoRequest request = new VideoRequest(prompt);
            request.setDuration(15);
            request.setAspectRatio("9:16");

            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("model", "sora-1.0");
            requestJson.addProperty("prompt", prompt);
            requestJson.addProperty("duration", 15);
            requestJson.addProperty("size", "720x1280"); // 9:16 aspect ratio

            String requestBody = gson.toJson(requestJson);
            Map<String, String> headers = createHeaders();
            String url = Constants.OPENAI_API_BASE_URL + SORA_ENDPOINT;

            HttpResponse<String> response = HttpUtil.post(url, requestBody, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.OPENAI_SORA,
                        "Failed to generate video directly: " + response.body(),
                        response.statusCode()
                );
            }

            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String videoUrl = responseJson.getAsJsonArray("data")
                    .get(0).getAsJsonObject()
                    .get("url").getAsString();

            logger.info("Video generated successfully: {}", videoUrl);
            return videoUrl;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.OPENAI_SORA, "Error generating video directly", e);
        }
    }

    /**
     * Creates HTTP headers with authorization
     */
    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Validates API key by making a simple request
     */
    public boolean validateApiKey() {
        try {
            // Try to list models or make a simple query
            String url = Constants.OPENAI_API_BASE_URL + "/models";
            Map<String, String> headers = createHeaders();
            HttpResponse<String> response = HttpUtil.get(url, headers);
            return HttpUtil.isSuccessful(response.statusCode());
        } catch (Exception e) {
            logger.error("API key validation failed", e);
            return false;
        }
    }

    /**
     * Gets the maximum video duration supported
     */
    public int getMaxDuration() {
        // Sora typically supports up to 20 seconds
        // For YouTube Shorts, we limit to 15 seconds
        return 15;
    }
}
