package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.pinterest.PinterestApiClient;
import com.videogenerator.pinterest.PinterestPublishService;
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

class PinterestBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    List<String> generateCalls;

    static class FakeClient extends PinterestApiClient {
        int createCalls = 0;

        FakeClient() {
            super(null, "cid", "csecret", "https://cb/", Path.of("/dev/null"), 0);
        }

        @Override
        public String findBoardIdByName(String boardName) {
            return "BOARD1";
        }

        @Override
        public String createPin(Path imageFile, String boardId, String title,
                                String description, String altText) {
            createCalls++;
            return "https://www.pinterest.com/pin/PIN" + createCalls + "/";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        Path pinterestDir = root.resolve("pinterest");
        Path batchDir = pinterestDir.resolve("batch-1");
        Files.createDirectories(batchDir);
        Files.write(batchDir.resolve("pin-01.png"), new byte[]{1, 2, 3});
        Files.writeString(batchDir.resolve("manifest.json"), """
            [{"file":"pin-01.png","title":"T1","description":"D1","altText":"A1","published":false}]""");

        generateCalls = new ArrayList<>();
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withPinterest(
                        new PinterestPublishService(new FakeClient(), "Small Space Living", pinterestDir),
                        (niche, count) -> generateCalls.add(niche + "|" + count));
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
        HttpResponse<String> res = get("/api/pinterest/batches");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"id\":\"batch-1\""));
        assertTrue(res.body().contains("\"title\":\"T1\""));
    }

    @Test
    void publishesPinAndPersists() throws Exception {
        HttpResponse<String> res = post("/api/pinterest/batches/batch-1/pins/0/publish", "");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("PIN1"));

        HttpResponse<String> listed = get("/api/pinterest/batches");
        assertTrue(listed.body().contains("\"published\":true"));
    }

    @Test
    void servesImageBytes() throws Exception {
        HttpResponse<byte[]> res = client.send(
                HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/pinterest/batches/batch-1/images/pin-01.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, res.statusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, res.body());
    }

    @Test
    void rejectsPathTraversalInImageFile() throws Exception {
        HttpResponse<String> res = get(
                "/api/pinterest/batches/batch-1/images/..%2F..%2Fsecret.png");

        assertEquals(400, res.statusCode());
    }

    @Test
    void triggersGeneration() throws Exception {
        HttpResponse<String> res = post("/api/pinterest/generate",
                "{\"niche\":\"cozy bedroom decor\",\"count\":5}");

        assertEquals(202, res.statusCode());
        assertEquals(1, generateCalls.size());
        assertEquals("cozy bedroom decor|5", generateCalls.get(0));
    }

    @Test
    void requiresNicheForGeneration() throws Exception {
        HttpResponse<String> res = post("/api/pinterest/generate", "{}");

        assertEquals(400, res.statusCode());
    }
}
