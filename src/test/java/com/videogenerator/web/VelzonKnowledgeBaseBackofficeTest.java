package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.velzon.VelzonKnowledgeBaseClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VelzonKnowledgeBaseBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    AtomicInteger listCalls;

    static class FakeKbHttp implements VelzonKnowledgeBaseClient.Http {
        final AtomicInteger calls;

        FakeKbHttp(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public String get(String url) {
            calls.incrementAndGet();
            return """
                <html><body>
                  <a href="/bilgi-merkezi/borsa-terimleri/acente-nedir/">Acente Nedir?</a>
                  <a href="/bilgi-merkezi/baslangic/velzon-endeksler-rehberi/">Endeksler Rehberi</a>
                </body></html>
                """;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        listCalls = new AtomicInteger(0);
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzonKnowledgeBase(new VelzonKnowledgeBaseClient(new FakeKbHttp(listCalls)));
        port = server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void listsArticles() throws Exception {
        HttpResponse<String> res = get("/api/velzon-knowledge-base/articles");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"title\":\"Acente Nedir?\""));
        assertTrue(res.body().contains("\"path\":\"/bilgi-merkezi/borsa-terimleri/acente-nedir/\""));
        assertTrue(res.body().contains("\"title\":\"Endeksler Rehberi\""));
    }

    @Test
    void cachesArticleListAfterFirstFetch() throws Exception {
        get("/api/velzon-knowledge-base/articles");
        get("/api/velzon-knowledge-base/articles");
        get("/api/velzon-knowledge-base/articles");

        assertEquals(1, listCalls.get(), "index page should only be fetched once, then cached");
    }

    @Test
    void returns503WhenNotConfigured() throws Exception {
        BackofficeServer unconfigured = new BackofficeServer(
                new JobService(new JobStore(root.resolve("jobs2")),
                        new ChannelStore(root.resolve("channels2")),
                        new CostTracker(root.resolve("costs2")), 100.0),
                ch -> { }, id -> { }, 0);
        Files.createDirectories(root.resolve("channels2"));
        int p = unconfigured.start();
        try {
            HttpResponse<String> res = client.send(
                    HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + p + "/api/velzon-knowledge-base/articles"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            unconfigured.stop();
        }
    }

    @Test
    void rejectsPostToArticlesRoute() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-knowledge-base/articles"))
                        .POST(HttpRequest.BodyPublishers.ofString("")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(405, res.statusCode());
    }

    @Test
    void returns404ForUnknownSubresource() throws Exception {
        HttpResponse<String> res = get("/api/velzon-knowledge-base/nonsense");

        assertEquals(404, res.statusCode());
    }
}
