package com.videogenerator.api;

import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.HttpUtil;
import com.videogenerator.util.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ElevenLabs Music (music_v2) client. Commercially licensed training data,
 * shared across all language variants of a job.
 */
public class MusicApiClient implements MusicGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MusicApiClient.class);
    private static final String MUSIC_URL = "https://api.elevenlabs.io/v1/music";

    private final Configuration config;
    private final String apiKey;

    public MusicApiClient() {
        this.config = Configuration.getInstance();
        this.apiKey = config.getTtsApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
            logger.warn("ElevenLabs API key is not configured. Music generation will not work.");
        }
    }

    static String buildRequestBody(String prompt, int durationSeconds, String modelId) {
        JsonObject body = new JsonObject();
        body.addProperty("prompt", prompt);
        body.addProperty("music_length_ms", durationSeconds * 1000);
        body.addProperty("model_id", modelId);
        return body.toString();
    }

    @Override
    public File generate(String prompt, int durationSeconds, Path out) throws ApiException {
        logger.info("Generating music ({}s): {}", durationSeconds, prompt);
        if (apiKey == null || apiKey.isEmpty() || apiKey.contains("YOUR_")) {
            throw new ApiException(ApiProvider.ELEVENLABS, "ElevenLabs API key is not configured");
        }
        try {
            return new RetryPolicy().execute(() -> {
                try {
                    String body = buildRequestBody(prompt, durationSeconds,
                            config.getMusicModel());
                    Map<String, String> headers = new HashMap<>();
                    headers.put("xi-api-key", apiKey);
                    headers.put("Content-Type", "application/json");

                    HttpResponse<byte[]> response =
                            HttpUtil.postBytes(MUSIC_URL, body, headers);
                    if (!HttpUtil.isSuccessful(response.statusCode())) {
                        String error = new String(response.body());
                        logger.error("Music API error: {}", error);
                        throw new RuntimeException(new ApiException(ApiProvider.ELEVENLABS,
                                "Music generation failed: " + error, response.statusCode()));
                    }
                    Files.createDirectories(out.getParent());
                    Files.write(out, response.body());
                    logger.info("Music written: {} ({} bytes)", out, response.body().length);
                    return out.toFile();
                } catch (IOException e) {
                    throw new RuntimeException(
                            new ApiException(ApiProvider.ELEVENLABS, "Music I/O error", e));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            new ApiException(ApiProvider.ELEVENLABS, "Music interrupted", e));
                }
            }, "ElevenLabs music generation");
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(ApiProvider.ELEVENLABS, "Music generation failed", e);
        } catch (Exception e) {
            throw new ApiException(ApiProvider.ELEVENLABS, "Music generation failed", e);
        }
    }
}
