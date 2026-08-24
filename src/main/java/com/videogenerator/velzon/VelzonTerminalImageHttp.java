package com.videogenerator.velzon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** VelzonTerminalImageClient.Http'nin JDK HttpClient implementasyonu. */
public class VelzonTerminalImageHttp implements VelzonTerminalImageClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public byte[] getBytes(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Velzon terminal image HTTP " + resp.statusCode() + ": " + url);
        }
        return resp.body();
    }
}
