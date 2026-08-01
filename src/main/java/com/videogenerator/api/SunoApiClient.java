package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.model.MusicRequest;
import com.videogenerator.model.MusicResponse;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import com.videogenerator.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Client for Suno API music generation
 */
public class SunoApiClient {
    private static final Logger logger = LoggerFactory.getLogger(SunoApiClient.class);
    private final Configuration config;
    private final Gson gson;
    private final String apiKey;

    public SunoApiClient() {
        this.config = Configuration.getInstance();
        this.gson = new Gson();
        this.apiKey = config.getSunoApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Suno API key is not configured");
        }
    }

    /**
     * Generates music based on a text prompt
     * This method initiates the generation and returns immediately with task IDs
     */
    public String[] generateMusic(String prompt) throws ApiException {
        logger.info("Generating music with prompt: {}", prompt);

        try {
            MusicRequest request = new MusicRequest(prompt);
            String requestBody = gson.toJson(request);

            Map<String, String> headers = createHeaders();
            String url = Constants.SUNO_API_BASE_URL + Constants.SUNO_GENERATE_ENDPOINT;

            HttpResponse<String> response = HttpUtil.post(url, requestBody, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.SUNO,
                        "Failed to generate music: " + response.body(),
                        response.statusCode()
                );
            }

            // Parse response to get task IDs
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);

            // Check if there's a data array
            if (responseJson.has("data") && responseJson.get("data").isJsonArray()) {
                JsonArray dataArray = responseJson.getAsJsonArray("data");
                String[] taskIds = new String[dataArray.size()];
                for (int i = 0; i < dataArray.size(); i++) {
                    JsonObject item = dataArray.get(i).getAsJsonObject();
                    taskIds[i] = item.get("id").getAsString();
                }
                logger.info("Music generation initiated with {} tasks", taskIds.length);
                return taskIds;
            }

            // Alternative response format - single task ID
            if (responseJson.has("id")) {
                String[] taskIds = new String[]{responseJson.get("id").getAsString()};
                logger.info("Music generation initiated with task ID: {}", taskIds[0]);
                return taskIds;
            }

            throw new ApiException(ApiProvider.SUNO, "Unexpected response format: " + response.body());

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.SUNO, "Error generating music", e);
        }
    }

    /**
     * Queries the status of music generation tasks
     */
    public MusicResponse[] queryMusicStatus(String[] taskIds) throws ApiException {
        logger.debug("Querying status for {} tasks", taskIds.length);

        try {
            // Build query string with task IDs
            String idsParam = String.join(",", taskIds);
            String url = Constants.SUNO_API_BASE_URL + Constants.SUNO_QUERY_ENDPOINT + "?ids=" + idsParam;

            Map<String, String> headers = createHeaders();
            HttpResponse<String> response = HttpUtil.get(url, headers);

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                throw new ApiException(
                        ApiProvider.SUNO,
                        "Failed to query music status: " + response.body(),
                        response.statusCode()
                );
            }

            // Parse response
            JsonArray dataArray;
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);

            if (responseJson.has("data") && responseJson.get("data").isJsonArray()) {
                dataArray = responseJson.getAsJsonArray("data");
            } else if (response.body().startsWith("[")) {
                // Response is directly an array
                dataArray = gson.fromJson(response.body(), JsonArray.class);
            } else {
                throw new ApiException(ApiProvider.SUNO, "Unexpected response format: " + response.body());
            }

            MusicResponse[] responses = new MusicResponse[dataArray.size()];
            for (int i = 0; i < dataArray.size(); i++) {
                responses[i] = gson.fromJson(dataArray.get(i), MusicResponse.class);
            }

            return responses;

        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(ApiProvider.SUNO, "Error querying music status", e);
        }
    }

    /**
     * Generates music and waits until it's ready (with polling)
     * Returns the first completed music response
     */
    public MusicResponse generateAndWait(String prompt) throws ApiException, InterruptedException {
        logger.info("Generating music and waiting for completion...");

        // Step 1: Initiate generation
        String[] taskIds = generateMusic(prompt);

        if (taskIds.length == 0) {
            throw new ApiException(ApiProvider.SUNO, "No task IDs returned from generation request");
        }

        // Step 2: Poll for completion
        long startTime = System.currentTimeMillis();
        long maxWaitTime = config.getSunoMaxWaitTime();
        int pollingInterval = config.getSunoPollingInterval();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            MusicResponse[] responses = queryMusicStatus(taskIds);

            // Check if any task is completed
            for (MusicResponse response : responses) {
                if (response.isCompleted() && response.getAudioUrl() != null) {
                    logger.info("Music generation completed: {} (duration: {}s)", response.getId(), response.getDuration());
                    return response;
                }

                if (response.isFailed()) {
                    throw new ApiException(ApiProvider.SUNO, "Music generation failed for task: " + response.getId());
                }
            }

            // Wait before next poll
            logger.debug("Music still processing, waiting {}ms before next check...", pollingInterval);
            Thread.sleep(pollingInterval);
        }

        throw new ApiException(
                ApiProvider.SUNO,
                "Music generation timeout after " + (maxWaitTime / 1000) + " seconds"
        );
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
     * Validates API key by making a simple query
     */
    public boolean validateApiKey() {
        try {
            String url = Constants.SUNO_API_BASE_URL + "/api/get_limit";
            Map<String, String> headers = createHeaders();
            HttpResponse<String> response = HttpUtil.get(url, headers);
            return HttpUtil.isSuccessful(response.statusCode());
        } catch (Exception e) {
            logger.error("API key validation failed", e);
            return false;
        }
    }
}
