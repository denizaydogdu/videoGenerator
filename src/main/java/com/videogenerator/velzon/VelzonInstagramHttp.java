package com.videogenerator.velzon;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VelzonInstagramApiClient.Http'nin JDK HttpClient implementasyonu.
 * graph.facebook.com'a giden MetaApiClient/GraphHttp ile aynı şekil —
 * form/query'de access_token, Authorization header yok — ama host
 * graph.instagram.com (Instagram Login akışı, Facebook Page yok).
 */
public class VelzonInstagramHttp implements VelzonInstagramApiClient.Http {
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
    public String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET().build();
        return send(req);
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Instagram HTTP " + resp.statusCode() + ": "
                    + (resp.body() == null ? "" : resp.body().substring(0,
                            Math.min(500, resp.body().length()))));
        }
        return resp.body();
    }
}
