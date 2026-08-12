package com.videogenerator.velzon;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * VelzonKnowledgeBaseClient.Http'nin JDK HttpClient implementasyonu.
 * Kimlik doğrulama gerekmez (herkese açık site); tarayıcı benzeri bir
 * User-Agent set edilir çünkü bazı siteler boş/varsayılan JDK User-Agent'ı
 * ile bot trafiği olarak engelleyebiliyor (manuel testte "Mozilla/5.0" ile
 * 200 döndüğü doğrulandı).
 */
public class VelzonKnowledgeBaseHttp implements VelzonKnowledgeBaseClient.Http {
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; VelzonBackoffice/1.0; +https://shorts.velzon.tr)";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("Velzon knowledge-base HTTP "
                    + resp.statusCode() + ": " + url);
        }
        return resp.body();
    }
}
