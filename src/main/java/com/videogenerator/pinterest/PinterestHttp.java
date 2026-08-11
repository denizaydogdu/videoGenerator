package com.videogenerator.pinterest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/** PinterestApiClient.Http'nin JDK HttpClient implementasyonu. */
public class PinterestHttp implements PinterestApiClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String postFormBasicAuth(String url, String clientId, String clientSecret,
                                    Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(req);
    }

    @Override
    public String postJson(String url, String bearerToken, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return send(req);
    }

    @Override
    public String get(String url, String bearerToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + bearerToken)
                .GET().build();
        return send(req);
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Pinterest HTTP " + resp.statusCode() + ": "
                    + (resp.body() == null ? "" : resp.body().substring(0,
                            Math.min(500, resp.body().length()))));
        }
        return resp.body();
    }
}
