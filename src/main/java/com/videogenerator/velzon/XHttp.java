package com.videogenerator.velzon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** XApiClient.Http'nin JDK HttpClient implementasyonu. */
public class XHttp implements XApiClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String post(String url, String authorizationHeader, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", authorizationHeader)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return send(req);
    }

    @Override
    public String postMultipart(String url, String authorizationHeader, byte[] fileBytes,
                                String filename) throws Exception {
        String boundary = "----VelzonXBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, fileBytes, filename);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", authorizationHeader)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return send(req);
    }

    private static byte[] buildMultipartBody(String boundary, byte[] fileBytes, String filename) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"media\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        parts.add(fileBytes);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int total = parts.stream().mapToInt(p -> p.length).sum();
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, result, pos, p.length);
            pos += p.length;
        }
        return result;
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("X HTTP " + resp.statusCode() + ": "
                    + (resp.body() == null ? "" : resp.body().substring(0,
                            Math.min(500, resp.body().length()))));
        }
        return resp.body();
    }
}
