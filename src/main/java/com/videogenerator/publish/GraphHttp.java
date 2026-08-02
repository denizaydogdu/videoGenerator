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

/**
 * MetaApiClient.Http'nin JDK HttpClient implementasyonu. Binary upload
 * rupload.facebook.com'a tek parça gider (Shorts dosyaları ~20MB;
 * Meta tek istekte 1GB'a kadar kabul eder).
 */
public class GraphHttp implements MetaApiClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET().build();
        return send(req);
    }

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
    public String postBinary(String url, Map<String, String> headers, Path file)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                // Canlı bulgu (2026-08-02): rupload octet-stream ister; yoksa
                // yanıltıcı ProcessingFailedError döner
                .header("Content-Type", "application/octet-stream");
        headers.forEach(builder::header);
        HttpRequest req = builder
                .POST(HttpRequest.BodyPublishers.ofFile(file)).build();
        return send(req);
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Meta HTTP " + resp.statusCode()
                    + " for " + req.uri().getHost() + ": " + truncate(resp.body()));
        }
        return resp.body();
    }

    private static String truncate(String s) {
        return s == null ? "" : s.substring(0, Math.min(500, s.length()));
    }
}
