package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.JobStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class BackofficeAuthTest {
    @TempDir
    Path root;

    BackofficeServer server;
    final HttpClient client = HttpClient.newHttpClient();

    private BackofficeServer newServer() throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        JobStore jobStore = new JobStore(root.resolve("jobs"));
        JobService service = new JobService(jobStore, new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);
        return new BackofficeServer(service, ch -> { }, id -> { }, 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> getWithAuth(int port, String user, String pass) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/channels")).GET();
        applyAuthHeader(req, user, pass);
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postGenerateWithAuth(int port, String user, String pass)
            throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/jobs/generate"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"channelId\":\"ch1\"}"));
        applyAuthHeader(req, user, pass);
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void applyAuthHeader(HttpRequest.Builder req, String user, String pass) {
        if (user != null) {
            String creds = Base64.getEncoder().encodeToString(
                    (user + ":" + pass).getBytes());
            req.header("Authorization", "Basic " + creds);
        }
    }

    @Test
    void withoutAuthConfiguredNoCredentialsRequired() throws Exception {
        server = newServer();
        int port = server.start();

        HttpResponse<String> res = getWithAuth(port, null, null);

        assertEquals(200, res.statusCode());
    }

    @Test
    void withAuthConfiguredRejectsMissingCredentials() throws Exception {
        server = newServer().withAuth("velzon", "secret123");
        int port = server.start();

        HttpResponse<String> res = getWithAuth(port, null, null);

        assertEquals(401, res.statusCode());
    }

    @Test
    void withAuthConfiguredRejectsWrongCredentials() throws Exception {
        server = newServer().withAuth("velzon", "secret123");
        int port = server.start();

        HttpResponse<String> res = getWithAuth(port, "velzon", "wrong");

        assertEquals(401, res.statusCode());
    }

    @Test
    void withAuthConfiguredAcceptsCorrectCredentials() throws Exception {
        server = newServer().withAuth("velzon", "secret123");
        int port = server.start();

        HttpResponse<String> res = getWithAuth(port, "velzon", "secret123");

        assertEquals(200, res.statusCode());
    }

    @Test
    void authAppliesToStaticRouteToo() throws Exception {
        server = newServer().withAuth("velzon", "secret123");
        int port = server.start();

        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, res.statusCode());
    }

    @Test
    void authAppliesToMutatingPostRoutesToo() throws Exception {
        // GET-only kapsam yeterli değil — bu özelliğin asıl koruması gereken
        // şey POST /api/jobs/generate gibi gerçek yayın/harcama tetikleyen
        // uç noktalar (bkz. review bulgusu).
        server = newServer().withAuth("velzon", "secret123");
        int port = server.start();

        HttpResponse<String> withoutCreds = postGenerateWithAuth(port, null, null);
        assertEquals(401, withoutCreds.statusCode());

        HttpResponse<String> withCreds = postGenerateWithAuth(port, "velzon", "secret123");
        assertEquals(202, withCreds.statusCode());
    }

    @Test
    void partialConfigUsernameOnlyLeavesServerUnauthenticated() throws Exception {
        // withAuth'un "ikisi de dolu değilse devre dışı" sözleşmesini kilitler
        // — kısmi config (sadece kullanıcı adı) sessizce auth'u YARIM açmamalı.
        server = newServer().withAuth("velzon", "");
        int port = server.start();

        HttpResponse<String> res = getWithAuth(port, null, null);

        assertEquals(200, res.statusCode());
    }
}
