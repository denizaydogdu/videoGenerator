package com.videogenerator.velzon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * VelzonBriefingClient.Http'nin JDK HttpClient implementasyonu. Django
 * tarafında DeepSeek çağrısı ~60sn'ye kadar sürebilir (senkron, akış yok)
 * — timeout buna göre cömert tutuldu.
 */
public class VelzonBriefingHttp implements VelzonBriefingClient.Http {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String get(String url, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .GET();
        headers.forEach(builder::header);
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Velzon briefing HTTP " + resp.statusCode() + ": "
                    + (resp.body() == null ? "" : resp.body().substring(0,
                            Math.min(500, resp.body().length()))));
        }
        return resp.body();
    }
}
