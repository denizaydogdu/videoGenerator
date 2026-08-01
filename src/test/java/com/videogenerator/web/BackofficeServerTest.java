package com.videogenerator.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.videogenerator.channel.ChannelStore;
import com.videogenerator.channel.ChannelStoreTest;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackofficeServerTest {
    @TempDir
    Path root;

    BackofficeServer server;
    JobStore jobStore;
    int port;
    final List<String> launched = new ArrayList<>();
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        Files.writeString(channels.resolve("ch1.json"),
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);
        server = new BackofficeServer(service, launched::add, 0);
        port = server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void channelsListedWithPendingCount() throws Exception {
        JobServiceTest.pendingJob(jobStore, "ch1", "T");
        HttpResponse<String> res = get("/api/channels");
        assertEquals(200, res.statusCode());
        JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
        assertEquals(1, arr.size());
        assertEquals("ch1", arr.get(0).getAsJsonObject().get("channelId").getAsString());
        assertEquals(1, arr.get(0).getAsJsonObject().get("pendingCount").getAsInt());
    }

    @Test
    void jobsListAndDetail() throws Exception {
        Job job = JobServiceTest.pendingJob(jobStore, "ch1", "My Title");
        HttpResponse<String> list = get("/api/jobs?channel=ch1");
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("My Title"));

        HttpResponse<String> detail = get("/api/jobs/" + job.getJobId());
        assertEquals(200, detail.statusCode());
        JsonObject o = JsonParser.parseString(detail.body()).getAsJsonObject();
        assertEquals(job.getJobId(), o.get("jobId").getAsString());

        assertEquals(404, get("/api/jobs/2099-01-01-000000-ffffffff").statusCode());
    }

    @Test
    void approveFlowMapsStatusCodes() throws Exception {
        Job job = JobServiceTest.pendingJob(jobStore, "ch1", "T");
        String url = "/api/jobs/" + job.getJobId() + "/approve";

        assertEquals(400, send("POST", url, "{\"platforms\":[]}").statusCode());
        assertEquals(204, send("POST", url, "{\"platforms\":[\"YOUTUBE\"]}").statusCode());
        assertEquals(409, send("POST", url, "{\"platforms\":[\"YOUTUBE\"]}").statusCode());
    }

    @Test
    void metadataPatchValidates() throws Exception {
        Job job = JobServiceTest.pendingJob(jobStore, "ch1", "T");
        String url = "/api/jobs/" + job.getJobId() + "/variants/en";
        String good = new Gson().toJson(java.util.Map.of(
                "title", "New", "description", "D", "hashtags", List.of("#a")));
        assertEquals(204, send("PATCH", url, good).statusCode());
        assertEquals(400, send("PATCH", url, "{}").statusCode());
    }

    @Test
    void generateQueuesLaunch() throws Exception {
        HttpResponse<String> res = send("POST", "/api/jobs/generate",
                "{\"channelId\":\"ch1\"}");
        assertEquals(202, res.statusCode());
        assertEquals(List.of("ch1"), launched);
        assertEquals(400, send("POST", "/api/jobs/generate", "{}").statusCode());
    }

    @Test
    void staticUiServed() throws Exception {
        HttpResponse<String> index = get("/");
        assertEquals(200, index.statusCode());
        assertTrue(index.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(index.body().contains("Shorts Fabrikası"));

        assertEquals(200, get("/style.css").statusCode());
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(404, get("/nope.txt").statusCode());
        assertEquals(400, get("/..%2Fsecret").statusCode());
    }

    @Test
    void statsReturnsBudget() throws Exception {
        HttpResponse<String> res = get("/api/stats");
        assertEquals(200, res.statusCode());
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(100.0, o.get("monthlyBudget").getAsDouble(), 1e-9);
    }
}
