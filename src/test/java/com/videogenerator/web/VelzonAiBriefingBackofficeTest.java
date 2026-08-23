package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
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

class VelzonAiBriefingBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    Path imagesDir;

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);

        imagesDir = root.resolve("velzon-ai-briefing");
        Path jobDir = imagesDir.resolve("job-1");
        Files.createDirectories(jobDir);
        Files.write(jobDir.resolve("image.png"), new byte[]{1, 2, 3});

        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzonAiBriefing(imagesDir);
        port = server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void servesImageBytes() throws Exception {
        HttpResponse<byte[]> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-ai-briefing/images/job-1/image.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, res.statusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, res.body());
    }

    @Test
    void returns404WhenImageMissing() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-ai-briefing/images/job-1/missing.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, res.statusCode());
    }

    @Test
    void rejectsPathTraversalInJobId() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-ai-briefing/images/..%2F..%2Fsecret/image.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, res.statusCode());
    }

    @Test
    void rejectsPathTraversalInFile() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/velzon-ai-briefing/images/job-1/..%2F..%2Fsecret.png"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

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
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p
                            + "/api/velzon-ai-briefing/images/job-1/image.png"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            unconfigured.stop();
        }
    }
}
