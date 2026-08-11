package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.velzon.VelzonPublishService;
import com.videogenerator.velzon.XApiClient;
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

class VelzonBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    List<String> generateCalls;

    static class FakeClient extends XApiClient {
        int postCalls = 0;

        FakeClient() {
            super(null, "cid", "csecret", "https://cb/", Path.of("/dev/null"));
        }

        @Override
        public String postTweet(String text) {
            postCalls++;
            return "https://x.com/i/status/TWEET" + postCalls;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        Path velzonDir = root.resolve("velzon");
        Path batchDir = velzonDir.resolve("batch-1");
        Files.createDirectories(batchDir);
        Files.writeString(batchDir.resolve("manifest.json"), """
            [{"topic":"T1","text":"tweet text","published":false}]""");

        generateCalls = new ArrayList<>();
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzon(new VelzonPublishService(new FakeClient(), velzonDir),
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
        HttpResponse<String> res = get("/api/velzon/batches");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"id\":\"batch-1\""));
        assertTrue(res.body().contains("\"text\":\"tweet text\""));
    }

    @Test
    void publishesTweetAndPersists() throws Exception {
        HttpResponse<String> res = post("/api/velzon/batches/batch-1/tweets/0/publish", "");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("TWEET1"));

        HttpResponse<String> listed = get("/api/velzon/batches");
        assertTrue(listed.body().contains("\"published\":true"));
    }

    @Test
    void triggersGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon/generate",
                "{\"topic\":\"e-fatura ipuçları\",\"count\":3}");

        assertEquals(202, res.statusCode());
        assertEquals(1, generateCalls.size());
        assertEquals("e-fatura ipuçları|3", generateCalls.get(0));
    }

    @Test
    void requiresTopicForGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon/generate", "{}");

        assertEquals(400, res.statusCode());
    }
}
