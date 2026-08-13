package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.velzon.VelzonInstagramApiClient;
import com.videogenerator.velzon.VelzonInstagramPublishService;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonInstagramBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    List<String> generateCalls;

    static class FakeClient extends VelzonInstagramApiClient {
        int containerCalls = 0;

        FakeClient() {
            super(null, "IGUSER1", "TOKEN1");
        }

        @Override
        public void waitUntilContainerReady(String creationId) {
            // testte gerçek polling'e gerek yok — container her zaman hazır kabul edilir
        }

        @Override
        public String createMediaContainer(String imageUrl, String caption) {
            containerCalls++;
            return "CONTAINER" + containerCalls;
        }

        @Override
        public String publishContainer(String creationId) {
            return "MEDIA" + containerCalls;
        }

        @Override
        public String getPermalink(String mediaId) {
            return "https://www.instagram.com/p/PERM" + containerCalls + "/";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        Path igDir = root.resolve("velzon-instagram");
        Path batchDir = igDir.resolve("batch-1");
        Files.createDirectories(batchDir);
        Files.write(batchDir.resolve("post-01.png"), new byte[]{1, 2, 3});
        Files.writeString(batchDir.resolve("manifest.json"), """
            [{"file":"post-01.png","caption":"C1 #efatura","published":false}]""");

        generateCalls = new ArrayList<>();
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzonInstagram(
                        new VelzonInstagramPublishService(new FakeClient(), igDir,
                                "https://shorts.velzon.tr"),
                        (topic, count) -> generateCalls.add(topic + "|" + count));
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
        HttpResponse<String> res = get("/api/velzon-instagram/batches");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"id\":\"batch-1\""));
        assertTrue(res.body().contains("\"caption\":\"C1 #efatura\""));
    }

    @Test
    void publishesPostAndPersists() throws Exception {
        HttpResponse<String> res = post(
                "/api/velzon-instagram/batches/batch-1/posts/0/publish", "");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("PERM1"));

        HttpResponse<String> listed = get("/api/velzon-instagram/batches");
        assertTrue(listed.body().contains("\"published\":true"));
    }

    @Test
    void servesImageBytes() throws Exception {
        HttpResponse<byte[]> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-instagram/batches/batch-1/images/post-01.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, res.statusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, res.body());
    }

    @Test
    void rejectsPathTraversalInImageFile() throws Exception {
        HttpResponse<String> res = get(
                "/api/velzon-instagram/batches/batch-1/images/..%2F..%2Fsecret.png");

        assertEquals(400, res.statusCode());
    }

    @Test
    void triggersGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon-instagram/generate",
                "{\"articlePath\":\"/bilgi-merkezi/borsa-terimleri/acente-nedir/\",\"count\":3}");

        assertEquals(202, res.statusCode());
        assertEquals(1, generateCalls.size());
        assertEquals("/bilgi-merkezi/borsa-terimleri/acente-nedir/|3", generateCalls.get(0));
    }

    @Test
    void requiresArticlePathForGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon-instagram/generate", "{}");

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
                            URI.create("http://127.0.0.1:" + p + "/api/velzon-instagram/batches"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            unconfigured.stop();
        }
    }
}
