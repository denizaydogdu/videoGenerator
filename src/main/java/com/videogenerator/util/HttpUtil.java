package com.videogenerator.util;

import com.videogenerator.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP utility class for making API requests
 */
public class HttpUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static HttpClient httpClient;

    static {
        Configuration config = Configuration.getInstance();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getHttpConnectTimeout()))
                .build();
    }

    /**
     * Performs a GET request
     */
    public static HttpResponse<String> get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        logger.debug("GET request to: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .GET();

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("GET response from {}: status={}, body length={}", url, response.statusCode(), response.body().length());
        return response;
    }

    /**
     * Performs a POST request with JSON body
     */
    public static HttpResponse<String> post(String url, String jsonBody, Map<String, String> headers)
            throws IOException, InterruptedException {
        logger.debug("POST request to: {} with body: {}", url, jsonBody);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("POST response from {}: status={}, body length={}", url, response.statusCode(), response.body().length());
        return response;
    }

    /**
     * Performs a POST request with form data
     */
    public static HttpResponse<String> postForm(String url, Map<String, String> formData, Map<String, String> headers)
            throws IOException, InterruptedException {
        StringBuilder formBody = new StringBuilder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (formBody.length() > 0) {
                formBody.append("&");
            }
            formBody.append(entry.getKey()).append("=").append(entry.getValue());
        }

        logger.debug("POST (form) request to: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()));

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("POST (form) response from {}: status={}", url, response.statusCode());
        return response;
    }

    /**
     * Performs a POST request and returns binary response (for audio/video files).
     */
    public static HttpResponse<byte[]> postBytes(String url, String jsonBody, Map<String, String> headers)
            throws IOException, InterruptedException {
        logger.debug("POST (bytes) request to: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        logger.debug("POST (bytes) response from {}: status={}, body size={} bytes",
                url, response.statusCode(), response.body().length);
        return response;
    }

    /**
     * Performs a PUT request with JSON body
     */
    public static HttpResponse<String> put(String url, String jsonBody, Map<String, String> headers)
            throws IOException, InterruptedException {
        logger.debug("PUT request to: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("PUT response from {}: status={}", url, response.statusCode());
        return response;
    }

    /**
     * Performs a DELETE request
     */
    public static HttpResponse<String> delete(String url, Map<String, String> headers)
            throws IOException, InterruptedException {
        logger.debug("DELETE request to: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Configuration.getInstance().getHttpReadTimeout()))
                .DELETE();

        addHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        logger.debug("DELETE response from {}: status={}", url, response.statusCode());
        return response;
    }

    /**
     * Adds headers to request builder
     */
    private static void addHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
        }
    }

    /**
     * Creates authorization header with Bearer token
     */
    public static Map<String, String> createBearerTokenHeaders(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    /**
     * Checks if HTTP status code indicates success (2xx)
     */
    public static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Checks if HTTP status code indicates client error (4xx)
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * Checks if HTTP status code indicates server error (5xx)
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * Checks if HTTP status code is retryable
     */
    public static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 500 || statusCode == 503 || statusCode == 504;
    }
}
