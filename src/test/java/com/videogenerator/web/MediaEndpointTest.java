package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.Job;
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

class MediaEndpointTest {
    @TempDir
    Path root;

    BackofficeServer server;
    JobStore jobStore;
    Job job;
    int port;
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        jobStore = new JobStore(root.resolve("jobs"));
        job = JobServiceTest.pendingJob(jobStore, "ch1", "T");

        Path dir = jobStore.dirFor(job.getJobId());
        Files.createDirectories(dir.resolve("renders"));
        Files.createDirectories(dir.resolve("scenes"));
        Files.write(dir.resolve("renders/en.mp4"), new byte[1000]); // 1000 bayt sahte mp4
        Files.write(dir.resolve("scenes/01.png"), new byte[]{1, 2, 3});

        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);
        server = new BackofficeServer(service, ch -> { }, id -> { }, 0);
        port = server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<byte[]> get(String path, String rangeHeader) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).GET();
        if (rangeHeader != null) {
            b.header("Range", rangeHeader);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void fullVideoWithoutRange() throws Exception {
        HttpResponse<byte[]> res = get("/api/jobs/" + job.getJobId() + "/render/en", null);
        assertEquals(200, res.statusCode());
        assertEquals(1000, res.body().length);
        assertEquals("video/mp4", res.headers().firstValue("Content-Type").orElse(""));
        assertEquals("bytes", res.headers().firstValue("Accept-Ranges").orElse(""));
    }

    @Test
    void partialVideoWithRange() throws Exception {
        HttpResponse<byte[]> res = get("/api/jobs/" + job.getJobId() + "/render/en",
                "bytes=100-199");
        assertEquals(206, res.statusCode());
        assertEquals(100, res.body().length);
        assertEquals("bytes 100-199/1000",
                res.headers().firstValue("Content-Range").orElse(""));
    }

    @Test
    void unsatisfiableRangeReturns416() throws Exception {
        HttpResponse<byte[]> res = get("/api/jobs/" + job.getJobId() + "/render/en",
                "bytes=5000-");
        assertEquals(416, res.statusCode());
        assertEquals("bytes */1000",
                res.headers().firstValue("Content-Range").orElse(""));
    }

    @Test
    void sceneImageServed() throws Exception {
        HttpResponse<byte[]> res = get("/api/jobs/" + job.getJobId() + "/scene/1", null);
        assertEquals(200, res.statusCode());
        assertEquals(3, res.body().length);
        assertEquals("image/png", res.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void sceneImageFallsBackToMultiImageNaming() throws Exception {
        // Format 2.0: sahne başına çoklu görsel — 02.png yok, 02a.png var
        Files.write(jobStore.dirFor(job.getJobId()).resolve("scenes/02a.png"),
                new byte[]{4, 5, 6, 7});
        HttpResponse<byte[]> res = get("/api/jobs/" + job.getJobId() + "/scene/2", null);
        assertEquals(200, res.statusCode());
        assertEquals(4, res.body().length);
    }

    @Test
    void invalidSegmentsRejected() throws Exception {
        assertEquals(400, get("/api/jobs/" + job.getJobId() + "/render/EN!", null).statusCode());
        assertEquals(400, get("/api/jobs/" + job.getJobId() + "/scene/abc", null).statusCode());
        assertEquals(404, get("/api/jobs/" + job.getJobId() + "/render/fr", null).statusCode());
    }
}
