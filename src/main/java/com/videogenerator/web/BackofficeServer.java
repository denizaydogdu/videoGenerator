package com.videogenerator.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.job.JobStatus;
import com.videogenerator.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Localhost-only review console. Thin HTTP layer over {@link JobService};
 * all domain rules live there. Binds to 127.0.0.1 exclusively — there is
 * no authentication, this is a single-user local tool.
 */
public class BackofficeServer {
    private static final Logger logger = LoggerFactory.getLogger(BackofficeServer.class);

    /**
     * Wired by Main to run JobPipeline asynchronously. Implementations MUST
     * serialize execution (e.g. single-thread executor): concurrent launches
     * of the same channel would duplicate spend.
     */
    public interface JobLauncher {
        void launch(String channelId);
    }

    private final JobService service;
    private final JobLauncher launcher;
    private final int requestedPort;
    private final Gson gson = new Gson();
    private HttpServer httpServer;
    private java.util.concurrent.ExecutorService executor;

    public BackofficeServer(JobService service, JobLauncher launcher, int port) {
        this.service = service;
        this.launcher = launcher;
        this.requestedPort = port;
    }

    /** @return the actual listening port (useful when constructed with 0) */
    public int start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        httpServer.createContext("/api", this::handleApi);
        httpServer.createContext("/", this::handleStatic);
        executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        httpServer.setExecutor(executor);
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        logger.info("Backoffice listening on http://127.0.0.1:{}", port);
        return port;
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ==================== API routing ====================

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            // Raw path: %2F must NOT merge/split segments before routing
            String[] seg = ex.getRequestURI().getRawPath().split("/");
            // /api/channels → ["", "api", "channels"]
            String method = ex.getRequestMethod();
            switch (seg.length) {
                case 3 -> {
                    switch (seg[2]) {
                        case "channels" -> requireGet(ex, method, this::channelsJson);
                        case "jobs" -> requireGet(ex, method, () -> jobsJson(ex));
                        case "stats" -> requireGet(ex, method, () -> gson.toJson(service.stats()));
                        default -> sendError(ex, 404, "Unknown resource");
                    }
                }
                case 4 -> {
                    if (!"jobs".equals(seg[2])) {
                        sendError(ex, 404, "Unknown resource");
                    } else if ("generate".equals(seg[3])) {
                        requirePost(ex, method, () -> generate(ex));
                    } else {
                        requireGet(ex, method, () ->
                                sendJson(ex, 200, gson.toJson(service.detail(seg[3]))));
                    }
                }
                case 5 -> {
                    if (!"jobs".equals(seg[2])) {
                        sendError(ex, 404, "Unknown resource");
                        return;
                    }
                    String jobId = seg[3];
                    switch (seg[4]) {
                        case "approve" -> requirePost(ex, method, () -> approve(ex, jobId));
                        case "reject" -> requirePost(ex, method, () -> {
                            service.reject(jobId);
                            sendNoContent(ex);
                        });
                        default -> sendError(ex, 404, "Unknown resource");
                    }
                }
                case 6 -> {
                    if ("jobs".equals(seg[2]) && "variants".equals(seg[4])
                            && "PATCH".equals(method)) {
                        patchMetadata(ex, seg[3], seg[5]);
                    } else {
                        sendError(ex, 404, "Unknown resource");
                    }
                }
                default -> sendError(ex, 404, "Unknown resource");
            }
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (IllegalStateException e) {
            sendError(ex, 409, e.getMessage());
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                sendError(ex, 404, "Not found");
            } else {
                logger.error("API error on {}", ex.getRequestURI(), e);
                sendError(ex, 500, "Internal error");
            }
        } finally {
            ex.close();
        }
    }

    private boolean isNotFound(RuntimeException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof java.nio.file.NoSuchFileException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private interface BodySender {
        void send() throws IOException;
    }

    private void requireGet(HttpExchange ex, String method,
                            java.util.function.Supplier<String> body) throws IOException {
        if (!"GET".equals(method)) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        sendJson(ex, 200, body.get());
    }

    private void requireGet(HttpExchange ex, String method, BodySender sender)
            throws IOException {
        if (!"GET".equals(method)) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        sender.send();
    }

    private void requirePost(HttpExchange ex, String method, BodySender sender)
            throws IOException {
        if (!"POST".equals(method)) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        sender.send();
    }

    private String channelsJson() {
        try {
            List<ChannelProfile> channels = service.channels().loadEnabled();
            List<Map<String, Object>> rows = channels.stream()
                    .map(c -> Map.<String, Object>of(
                            "channelId", c.getChannelId(),
                            "displayName", c.getDisplayName() == null
                                    ? c.getChannelId() : c.getDisplayName(),
                            "pendingCount", service.listJobs(c.getChannelId(),
                                    JobStatus.PENDING_REVIEW.name()).size()))
                    .toList();
            return gson.toJson(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void jobsJson(HttpExchange ex) throws IOException {
        Map<String, String> q = queryParams(ex.getRequestURI().getQuery());
        sendJson(ex, 200, gson.toJson(
                service.listJobs(q.get("channel"), q.get("status"))));
    }

    private void generate(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String channelId = body.has("channelId")
                ? body.get("channelId").getAsString() : null;
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId is required");
        }
        launcher.launch(channelId);
        sendJson(ex, 202, "{\"queued\":\"" + channelId + "\"}");
    }

    private void approve(HttpExchange ex, String jobId) throws IOException {
        JsonObject body = readJson(ex);
        List<String> platforms;
        try {
            platforms = body.has("platforms")
                    ? gson.fromJson(body.get("platforms"),
                            new com.google.gson.reflect.TypeToken<List<String>>() { }.getType())
                    : List.of();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("platforms must be a string array");
        }
        service.approve(jobId, platforms);
        sendNoContent(ex);
    }

    private void patchMetadata(HttpExchange ex, String jobId, String lang)
            throws IOException {
        VideoMetadata metadata;
        try {
            metadata = gson.fromJson(
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    VideoMetadata.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed metadata JSON");
        }
        service.updateMetadata(jobId, lang, metadata);
        sendNoContent(ex);
    }

    // ==================== static (Task B5 fills web/) ====================

    private void handleStatic(HttpExchange ex) throws IOException {
        sendError(ex, 404, "Not found");
        ex.close();
    }

    // ==================== helpers ====================

    private JsonObject readJson(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            JsonObject o = raw.isBlank() ? new JsonObject()
                    : JsonParser.parseString(raw).getAsJsonObject();
            return o;
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Malformed JSON body");
        }
    }

    private Map<String, String> queryParams(String query) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        if (query == null) {
            return map;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && eq < pair.length() - 1) {
                map.put(java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(204, -1);
    }

    private void sendError(HttpExchange ex, int code, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", message == null ? "error" : message);
        sendJson(ex, code, o.toString());
    }
}
