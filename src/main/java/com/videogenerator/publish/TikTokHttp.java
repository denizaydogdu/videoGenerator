package com.videogenerator.publish;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** TikTokApiClient.Http'nin JDK HttpClient implementasyonu. */
public class TikTokHttp implements TikTokApiClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String postForm(String url, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(req);
    }

    @Override
    public String postJson(String url, String bearerToken, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(req);
    }

    @Override
    public String putChunk(String url, String contentRange, Path file) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Range", contentRange)
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofFile(file)).build();
        return send(req);
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("TikTok HTTP " + resp.statusCode() + ": "
                    + (resp.body() == null ? "" : resp.body().substring(0,
                            Math.min(500, resp.body().length()))));
        }
        return resp.body();
    }
}
