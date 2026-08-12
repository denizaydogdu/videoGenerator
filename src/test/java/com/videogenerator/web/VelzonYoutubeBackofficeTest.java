package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import com.videogenerator.velzon.VelzonYoutubePublishService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonYoutubeBackofficeTest {
    @TempDir
    Path root;

    BackofficeServer server;
    int port;
    final HttpClient client = HttpClient.newHttpClient();
    List<String> generateCalls;

    static class FakeUploader implements VelzonYoutubePublishService.Uploader {
        int calls = 0;

        @Override
        public UploadResult upload(File videoFile, VideoMetadata metadata) {
            calls++;
            return new UploadResult("VID" + calls, metadata.getTitle());
        }
    }

    static class FakeVideoBuilder implements VelzonYoutubePublishService.VideoBuilder {
        @Override
        public File build(Path batchDir, VelzonYoutubePublishService.ScriptEntry entry, int index)
                throws Exception {
            Path video = batchDir.resolve("fake.mp4");
            Files.writeString(video, "fake");
            return video.toFile();
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
              "hashtags":["#efatura"],"imagePrompt":"P1",
              "imageFile":"video-01.png","published":false}]""");

        generateCalls = new ArrayList<>();
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0)
                .withVelzonYoutube(
                        new VelzonYoutubePublishService(new FakeUploader(), new FakeVideoBuilder(), ytDir),
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
        HttpResponse<String> res = get("/api/velzon-youtube/batches");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"id\":\"batch-1\""));
        assertTrue(res.body().contains("\"title\":\"T1\""));
    }

    @Test
    void publishesVideoAndPersists() throws Exception {
        HttpResponse<String> res = post(
                "/api/velzon-youtube/batches/batch-1/scripts/0/publish", "");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("VID1"));

        HttpResponse<String> listed = get("/api/velzon-youtube/batches");
        assertTrue(listed.body().contains("\"published\":true"));
    }

    @Test
    void triggersGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon-youtube/generate",
                "{\"articlePath\":\"/bilgi-merkezi/borsa-terimleri/acente-nedir/\",\"count\":3}");

        assertEquals(202, res.statusCode());
        assertEquals(1, generateCalls.size());
        assertEquals("/bilgi-merkezi/borsa-terimleri/acente-nedir/|3", generateCalls.get(0));
    }

    @Test
    void requiresArticlePathForGeneration() throws Exception {
        HttpResponse<String> res = post("/api/velzon-youtube/generate", "{}");

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
                            URI.create("http://127.0.0.1:" + p + "/api/velzon-youtube/batches"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            unconfigured.stop();
        }
    }
}
