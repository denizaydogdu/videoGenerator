package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.velzon.VelzonTiktokPublishService;
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

import static org.junit.jupiter.api.Assertions.*;

class VelzonTiktokBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();

    static class FakePoster implements VelzonTiktokPublishService.Poster {
        int calls = 0;

        @Override
        public String post(Path video, String title, String privacyLevel) {
            calls++;
            return "publish_id_" + calls;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        Path ytDir = root.resolve("velzon-youtube");
        Path batchDir = ytDir.resolve("batch-1");
        Files.createDirectories(batchDir);
        Files.writeString(batchDir.resolve("manifest.json"), """
            [{"narration":"N1","title":"T1","description":"D1",
              "hashtags":["#borsa"],"imagePrompt":"P1",
              "imageFile":"video-01.png","published":true,
              "url":"https://www.youtube.com/shorts/YT1"}]""");
        Files.writeString(batchDir.resolve("video-01.mp4"), "fake-mp4-bytes");

        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzonTiktok(new VelzonTiktokPublishService(new FakePoster(), ytDir, "SELF_ONLY"));
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

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void listsBatches() throws Exception {
        HttpResponse<String> res = get("/api/velzon-tiktok/batches");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"id\":\"batch-1\""));
        assertTrue(res.body().contains("\"videoReady\":true"));
    }

    @Test
    void publishesPostAndPersists() throws Exception {
        HttpResponse<String> res = post(
                "/api/velzon-tiktok/batches/batch-1/scripts/0/publish", "");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("publish_id_1"));

        HttpResponse<String> listed = get("/api/velzon-tiktok/batches");
        assertTrue(listed.body().contains("\"tiktokPublished\":true"));
    }

    @Test
    void rejectsPathTraversalInBatchId() throws Exception {
        HttpResponse<String> res = post(
                "/api/velzon-tiktok/batches/..%2F..%2Fescape/scripts/0/publish", "");

        assertEquals(400, res.statusCode());
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
                            URI.create("http://127.0.0.1:" + p + "/api/velzon-tiktok/batches"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            unconfigured.stop();
        }
    }
}
