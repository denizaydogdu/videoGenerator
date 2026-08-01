package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import com.videogenerator.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI gpt-image-2 client. Generates one vertical scene image per call
 * and writes the decoded PNG to the given path.
 */
public class ImageApiClient implements ImageGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ImageApiClient.class);
    private static final String IMAGES_ENDPOINT = "/images/generations";

    private final Configuration config;
    private final Gson gson = new Gson();
    private final String apiKey;

    public ImageApiClient() {
        this.config = Configuration.getInstance();
        this.apiKey = config.getOpenAiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
    }

    static String buildRequestBody(String model, String prompt, String size, String quality) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("prompt", prompt);
        body.addProperty("size", size);
        body.addProperty("quality", quality);
        body.addProperty("n", 1);
        return body.toString();
    }

    @Override
    public File generate(String prompt, Path outFile) throws ApiException {
        logger.info("Generating image: {}", prompt);
        try {
            String body = buildRequestBody(config.getImageModel(), prompt,
                    config.getImageSize(), config.getImageQuality());
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("Content-Type", "application/json");

            String url = Constants.OPENAI_API_BASE_URL + IMAGES_ENDPOINT;
            HttpResponse<String> response = new com.videogenerator.util.RetryPolicy()
                    .execute(() -> {
                        try {
                            HttpResponse<String> r = HttpUtil.post(url, body, headers);
                            int code = r.statusCode();
                            if (code == 429 || code >= 500) {
                                // ApiException cause makes RetryPolicy classify as retryable
                                throw new RuntimeException(new ApiException(
                                        ApiProvider.OPENAI_GPT,
                                        "Retryable image API status " + code, code));
                            }
                            return r;
                        } catch (java.io.IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }, "image generation");

            if (!HttpUtil.isSuccessful(response.statusCode())) {
                logger.error("Image API error: {}", response.body());
                throw new ApiException(ApiProvider.OPENAI_GPT,
                        "Image generation failed: " + response.body(), response.statusCode());
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String b64 = json.getAsJsonArray("data")
                    .get(0).getAsJsonObject()
                    .get("b64_json").getAsString();

            Files.createDirectories(outFile.getParent());
            Files.write(outFile, Base64.getDecoder().decode(b64));
            logger.info("Image written: {}", outFile);
            return outFile.toFile();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiProvider.OPENAI_GPT, "Error generating image", e);
        }
    }
}
