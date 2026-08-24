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
 * Localhost-only review console (nginx bunu prod'da `shorts.velzon.tr`
 * altında herkese açık olarak reverse-proxy'ler). Thin HTTP layer over
 * {@link JobService}; all domain rules live there. Binds to 127.0.0.1
 * exclusively. HTTP Basic Auth opsiyonel — bkz. {@link #withAuth}; hiç
 * çağrılmazsa (yerel geliştirme varsayılanı) kimlik doğrulaması YOK.
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

    /** Pinterest yeni parti üretimini arka planda tetikler (video JobLauncher'ıyla aynı desen). */
    public interface PinterestBatchLauncher {
        void launch(String nichePrompt, int count);
    }

    private final JobService service;
    private final JobLauncher launcher;
    private final JobLauncher publishLauncher;
    private final int requestedPort;
    private final Gson gson = new Gson();
    private StatsCollector statsCollector; // opsiyonel — yoksa /stats 503
    private com.videogenerator.pinterest.PinterestPublishService pinterestService; // opsiyonel
    private PinterestBatchLauncher pinterestGenerator; // opsiyonel

    /**
     * Velzon tweet partisi üretimini arka planda tetikler. articlePath,
     * Velzon bilgi merkezindeki seçili makalenin göreli yoludur (ör.
     * "/bilgi-merkezi/borsa-terimleri/acente-nedir/") — serbest metin bir
     * "topic" DEĞİL. Makalenin tam metnini çekip generateBatch'e geçirmek
     * bu launcher'ın implementasyonunun (Main'de) işidir.
     */
    public interface VelzonBatchLauncher {
        void launch(String articlePath, int count);
    }

    private com.videogenerator.velzon.VelzonPublishService velzonService; // opsiyonel
    private VelzonBatchLauncher velzonGenerator; // opsiyonel

    /** Velzon Instagram gönderi partisi üretimini arka planda tetikler — bkz. VelzonBatchLauncher javadoc'u. */
    public interface VelzonInstagramBatchLauncher {
        void launch(String articlePath, int count);
    }

    private com.videogenerator.velzon.VelzonInstagramPublishService velzonInstagramService; // opsiyonel
    private VelzonInstagramBatchLauncher velzonInstagramGenerator; // opsiyonel

    /** Velzon Facebook gönderi partisi üretimini arka planda tetikler — bkz. VelzonBatchLauncher javadoc'u. */
    public interface VelzonFacebookBatchLauncher {
        void launch(String articlePath, int count);
    }

    private com.videogenerator.velzon.VelzonFacebookPublishService velzonFacebookService; // opsiyonel
    private VelzonFacebookBatchLauncher velzonFacebookGenerator; // opsiyonel

    /** Velzon YouTube senaryo partisi üretimini arka planda tetikler — bkz. VelzonBatchLauncher javadoc'u. */
    public interface VelzonYoutubeBatchLauncher {
        void launch(String articlePath, int count);
    }

    private com.videogenerator.velzon.VelzonYoutubePublishService velzonYoutubeService; // opsiyonel
    private VelzonYoutubeBatchLauncher velzonYoutubeGenerator; // opsiyonel

    // "generate" yok — video zaten Velzon YouTube batch'i tarafından üretilmiş
    // olmalı, bu bölüm onu ikinci bir hesaba (@velzon_tr) yeniden postlar.
    private com.videogenerator.velzon.VelzonTiktokPublishService velzonTiktokService; // opsiyonel

    // Velzon'un gerçek bilgi merkezi makalelerini listeler; X/Instagram/YouTube
    // "Yeni parti üret" diyaloglarındaki serbest metin alanı yerine kullanılır.
    private com.videogenerator.velzon.VelzonKnowledgeBaseClient velzonKnowledgeBaseClient; // opsiyonel
    // İlk istekte doldurulur, süresiz cache'lenir (tek kullanıcılı iç araç —
    // PinterestPublishService'teki basitlik seviyesiyle tutarlı, aşırı
    // mühendislik yapılmadı). Sunucuyu yeniden başlatmak listeyi tazeler.
    private List<com.videogenerator.velzon.VelzonKnowledgeBaseClient.Article> cachedVelzonArticles;

    public BackofficeServer withStats(StatsCollector collector) {
        this.statsCollector = collector;
        return this;
    }

    public BackofficeServer withPinterest(
            com.videogenerator.pinterest.PinterestPublishService pinterestService,
            PinterestBatchLauncher pinterestGenerator) {
        this.pinterestService = pinterestService;
        this.pinterestGenerator = pinterestGenerator;
        return this;
    }

    public BackofficeServer withVelzon(
            com.videogenerator.velzon.VelzonPublishService velzonService,
            VelzonBatchLauncher velzonGenerator) {
        this.velzonService = velzonService;
        this.velzonGenerator = velzonGenerator;
        return this;
    }

    public BackofficeServer withVelzonInstagram(
            com.videogenerator.velzon.VelzonInstagramPublishService velzonInstagramService,
            VelzonInstagramBatchLauncher velzonInstagramGenerator) {
        this.velzonInstagramService = velzonInstagramService;
        this.velzonInstagramGenerator = velzonInstagramGenerator;
        return this;
    }

    public BackofficeServer withVelzonFacebook(
            com.videogenerator.velzon.VelzonFacebookPublishService velzonFacebookService,
            VelzonFacebookBatchLauncher velzonFacebookGenerator) {
        this.velzonFacebookService = velzonFacebookService;
        this.velzonFacebookGenerator = velzonFacebookGenerator;
        return this;
    }

    public BackofficeServer withVelzonYoutube(
            com.videogenerator.velzon.VelzonYoutubePublishService velzonYoutubeService,
            VelzonYoutubeBatchLauncher velzonYoutubeGenerator) {
        this.velzonYoutubeService = velzonYoutubeService;
        this.velzonYoutubeGenerator = velzonYoutubeGenerator;
        return this;
    }

    public BackofficeServer withVelzonTiktok(
            com.videogenerator.velzon.VelzonTiktokPublishService velzonTiktokService) {
        this.velzonTiktokService = velzonTiktokService;
        return this;
    }

    public BackofficeServer withVelzonKnowledgeBase(
            com.videogenerator.velzon.VelzonKnowledgeBaseClient velzonKnowledgeBaseClient) {
        this.velzonKnowledgeBaseClient = velzonKnowledgeBaseClient;
        return this;
    }

    // AI Brifing işi (VelzonAiBriefingJob) tamamen otomatik postlar — burada
    // manifest/batch/publish YOK, sadece ürettiği görselleri X/Instagram/
    // Facebook'un çektiği public URL üzerinden servis etmek için kök dizin.
    private java.nio.file.Path velzonAiBriefingImagesDir; // opsiyonel

    public BackofficeServer withVelzonAiBriefing(java.nio.file.Path velzonAiBriefingImagesDir) {
        this.velzonAiBriefingImagesDir = velzonAiBriefingImagesDir;
        return this;
    }

    private String authUsername; // opsiyonel — boşsa auth devre dışı (yerel geliştirme)
    private String authPassword;

    /**
     * HTTP Basic Auth etkinleştirir — ikisi de dolu verilmezse (varsayılan)
     * sunucu eskisi gibi kimlik doğrulamasız kalır (yerel `serve` kullanımı
     * bozulmaz). Prod'da (`shorts.velzon.tr`, nginx arkasında ama herkese
     * açık) etkinleştirilmesi önerilir — gerçek yayın tetikleyicileri ve
     * bütçe tüketen aksiyonlar içeriyor.
     */
    public BackofficeServer withAuth(String username, String password) {
        this.authUsername = username;
        this.authPassword = password;
        return this;
    }

    private HttpServer httpServer;
    private java.util.concurrent.ExecutorService executor;

    /**
     * @param publishLauncher invoked with the jobId after a successful
     *                        approval; runs publishing asynchronously
     */
    public BackofficeServer(JobService service, JobLauncher launcher,
                            JobLauncher publishLauncher, int port) {
        this.service = service;
        this.launcher = launcher;
        this.publishLauncher = publishLauncher;
        this.requestedPort = port;
    }

    /**
     * @return the actual listening port (useful when constructed with 0)
     *
     * NOT: {@code /api} context'ine blanket bir {@code Authenticator}
     * uygulanmıyor — Instagram/Facebook'un Content Publishing API'si post
     * atmadan önce görsel URL'sini KİMLİK BİLGİSİ OLMADAN kendi
     * sunucularından çeker (bkz. servePinterestImage/serveVelzonInstagramImage/
     * serveVelzonFacebookImage/serveVelzonAiBriefingImage). Bunlar bloklu
     * kalırsa Meta "medya indirilemedi" hatasıyla postu reddeder — canlıda
     * tam olarak bu yaşandı (2026-08-24 10:30 turu). Bu yüzden auth kontrolü
     * {@link #handleApi} içinde ELLE yapılıyor, dört public görsel route'u
     * ({@link #isPublicImageRoute}) hariç tutularak. Statik site ({@code /})
     * için istisna gerekmiyor, orada JDK'nın {@code Authenticator}'ı yeterli.
     */
    public int start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", requestedPort), 0);
        httpServer.createContext("/api", this::handleApi);
        var staticContext = httpServer.createContext("/", this::handleStatic);
        if (authConfigured()) {
            staticContext.setAuthenticator(buildAuthenticator());
            logger.info("Backoffice HTTP Basic Auth enabled");
        } else {
            logger.warn("Backoffice auth NOT configured — server is UNPROTECTED "
                    + "(backoffice.auth.username/password empty)");
        }
        executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        httpServer.setExecutor(executor);
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        logger.info("Backoffice listening on http://127.0.0.1:{}", port);
        return port;
    }

    private boolean authConfigured() {
        return authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank();
    }

    private com.sun.net.httpserver.Authenticator buildAuthenticator() {
        return new com.sun.net.httpserver.BasicAuthenticator("shorts-backoffice") {
            @Override
            public boolean checkCredentials(String user, String pwd) {
                return credentialsMatch(user, pwd);
            }
        };
    }

    /**
     * Sabit-zamanlı karşılaştırma — timing attack'e karşı (Django tarafındaki
     * hmac.compare_digest ile aynı prensip). "&" (kısa devre YOK) kasıtlı:
     * "&&" olsaydı yanlış kullanıcı adında şifre hiç karşılaştırılmaz, bu da
     * kullanıcı adının doğru olup olmadığını zamanlamadan sızdırabilirdi.
     */
    private boolean credentialsMatch(String user, String pwd) {
        boolean userMatch = java.security.MessageDigest.isEqual(
                authUsername.getBytes(StandardCharsets.UTF_8),
                user.getBytes(StandardCharsets.UTF_8));
        boolean passMatch = java.security.MessageDigest.isEqual(
                authPassword.getBytes(StandardCharsets.UTF_8),
                pwd.getBytes(StandardCharsets.UTF_8));
        return userMatch & passMatch;
    }

    /**
     * Meta'nın (Instagram/Facebook Content Publishing API) postlamadan önce
     * kimlik bilgisi OLMADAN çektiği görsel servis route'ları — auth
     * kontrolünden muaf tutulmalı. Segment şekilleri ilgili handleXxxApi
     * metotlarındaki route eşleşmesiyle birebir aynı.
     *
     * NOT: Pinterest burada YOK — Pinterest görseli base64 olarak doğrudan
     * `media_source` JSON'ına gömer ({@link com.videogenerator.pinterest.PinterestApiClient#createPin}),
     * Pinterest'in sunucuları bizim URL'imizi hiç çekmez. O route sadece
     * backoffice UI'daki insan önizlemesi için var — UI zaten `/` context'inin
     * Authenticator'ı üzerinden korunuyor, ayrı bir muafiyete gerek yok.
     * (2026-08-24 hotfix review'unda bulundu — ilk halinde yanlışlıkla
     * eklenmişti, gereksiz yere kimlik doğrulamasız bırakıyordu.)
     */
    static boolean isPublicImageRoute(String[] seg) {
        if (seg.length < 3) {
            return false;
        }
        String feature = seg[2];
        if ("velzon-ai-briefing".equals(feature)) {
            return seg.length == 6 && "images".equals(seg[3]);
        }
        if ("velzon-instagram".equals(feature) || "velzon-facebook".equals(feature)) {
            return seg.length == 7 && "batches".equals(seg[3]) && "images".equals(seg[5]);
        }
        return false;
    }

    private boolean apiRequestAuthorized(HttpExchange ex, String[] seg) {
        if (!authConfigured() || isPublicImageRoute(seg)) {
            return true;
        }
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(java.util.Base64.getDecoder().decode(header.substring(6)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        return credentialsMatch(decoded.substring(0, colon), decoded.substring(colon + 1));
    }

    private void sendUnauthorized(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"shorts-backoffice\"");
        sendError(ex, 401, "Unauthorized");
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
            if (!apiRequestAuthorized(ex, seg)) {
                sendUnauthorized(ex);
                return;
            }
            String method = ex.getRequestMethod();
            if (seg.length >= 3 && "pinterest".equals(seg[2])) {
                handlePinterestApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-instagram".equals(seg[2])) {
                handleVelzonInstagramApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-facebook".equals(seg[2])) {
                handleVelzonFacebookApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-ai-briefing".equals(seg[2])) {
                handleVelzonAiBriefingApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-knowledge-base".equals(seg[2])) {
                handleVelzonKnowledgeBaseApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-youtube".equals(seg[2])) {
                handleVelzonYoutubeApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon-tiktok".equals(seg[2])) {
                handleVelzonTiktokApi(ex, method, seg);
                return;
            }
            if (seg.length >= 3 && "velzon".equals(seg[2])) {
                handleVelzonApi(ex, method, seg);
                return;
            }
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
                    if ("channels".equals(seg[2])) {
                        handleChannel(ex, method, seg[3]);
                    } else if (!"jobs".equals(seg[2])) {
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
                        case "stats" -> requireGet(ex, method, () -> {
                            if (statsCollector == null) {
                                sendError(ex, 503, "Stats not configured");
                                return;
                            }
                            sendJson(ex, 200, gson.toJson(
                                    statsCollector.collect(service.jobs().load(jobId))));
                        });
                        default -> sendError(ex, 404, "Unknown resource");
                    }
                }
                case 6 -> {
                    if (!"jobs".equals(seg[2])) {
                        sendError(ex, 404, "Unknown resource");
                        return;
                    }
                    switch (seg[4]) {
                        case "render" -> requireGet(ex, method,
                                () -> serveRender(ex, seg[3], seg[5]));
                        case "scene" -> requireGet(ex, method,
                                () -> serveScene(ex, seg[3], seg[5]));
                        case "variants" -> {
                            if ("PATCH".equals(method)) {
                                patchMetadata(ex, seg[3], seg[5]);
                            } else {
                                sendError(ex, 405, "Method not allowed");
                            }
                        }
                        default -> sendError(ex, 404, "Unknown resource");
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
        try {
            publishLauncher.launch(jobId); // onay başarılıysa async yayın
        } catch (RuntimeException e) {
            // Onay kalıcı; yayın kuyruklanamadıysa 'publish <jobId>' ile
            // elle tetiklenir. 500 dönmek yanlış retry'a yol açar.
            logger.error("Approved {} but publish enqueue failed", jobId, e);
        }
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

    // ==================== Pinterest ====================

    private void handlePinterestApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (pinterestService == null) {
            sendError(ex, 503, "Pinterest not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                switch (seg[3]) {
                    case "batches" -> requireGet(ex, method, () ->
                            sendJson(ex, 200, gson.toJson(unchecked(pinterestService::listBatches))));
                    case "generate" -> requirePost(ex, method, () -> generatePinterestBatch(ex));
                    default -> sendError(ex, 404, "Unknown resource");
                }
            }
            case 7 -> {
                if (!"batches".equals(seg[3]) || !"images".equals(seg[5])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requireGet(ex, method, () -> servePinterestImage(ex, seg[4], seg[6]));
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"pins".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishPinterestPin(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /** Checked exception'ı domain tipine göre korur, aksi halde RuntimeException'a sarar. */
    private <T> T unchecked(ThrowingSupplier<T> op) {
        try {
            return op.get();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generatePinterestBatch(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String niche = body.has("niche") ? body.get("niche").getAsString() : null;
        if (niche == null || niche.isBlank()) {
            throw new IllegalArgumentException("niche is required");
        }
        int count = body.has("count") ? body.get("count").getAsInt() : 10;
        if (pinterestGenerator == null) {
            sendError(ex, 503, "Pinterest generator not configured");
            return;
        }
        pinterestGenerator.launch(niche, count);
        sendJson(ex, 202, "{\"queued\":true}");
    }

    private void publishPinterestPin(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid pin index");
        }
        var updated = unchecked(() -> pinterestService.publishPin(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    // ==================== Velzon AI Brifing ====================

    /** {@code GET /api/velzon-ai-briefing/images/{jobId}/{file}} — VelzonAiBriefingJob'ın ürettiği görseli servis eder. */
    private void handleVelzonAiBriefingApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonAiBriefingImagesDir == null) {
            sendError(ex, 503, "Velzon AI briefing not configured");
            return;
        }
        if (seg.length != 6 || !"images".equals(seg[3])) {
            sendError(ex, 404, "Unknown resource");
            return;
        }
        requireGet(ex, method, () -> serveVelzonAiBriefingImage(ex, seg[4], seg[5]));
    }

    private void serveVelzonAiBriefingImage(HttpExchange ex, String jobId, String file)
            throws IOException {
        java.nio.file.Path path = unchecked(() -> resolveVelzonAiBriefingImage(jobId, file));
        if (!java.nio.file.Files.exists(path)) {
            sendError(ex, 404, "Image not found");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private java.nio.file.Path resolveVelzonAiBriefingImage(String jobId, String file) {
        if (jobId == null || jobId.contains("/") || jobId.contains("\\") || jobId.contains("..")) {
            throw new IllegalArgumentException("Invalid jobId: " + jobId);
        }
        if (file == null || file.contains("/") || file.contains("\\") || file.contains("..")) {
            throw new IllegalArgumentException("Invalid file: " + file);
        }
        java.nio.file.Path root = velzonAiBriefingImagesDir.normalize();
        java.nio.file.Path path = root.resolve(jobId).resolve(file).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes directory: " + jobId);
        }
        return path;
    }

    private void servePinterestImage(HttpExchange ex, String batchId, String file)
            throws IOException {
        java.nio.file.Path path = unchecked(
                () -> pinterestService.imageFile(batchId, file));
        if (!java.nio.file.Files.exists(path)) {
            sendError(ex, 404, "Image not found");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== Velzon Knowledge Base ====================

    /**
     * Velzon'un gerçek bilgi merkezi makalelerini (başlık + yol) döner —
     * X/Instagram/YouTube "Yeni parti üret" diyaloglarındaki makale seçici
     * bu listeyle beslenir. İçerik (article body) burada YOK: listArticles()
     * ~400 makaleyi tek tek indirmez, tam metin yalnızca generate sırasında
     * seçilen tek makale için VelzonKnowledgeBaseClient.fetchArticle() ile
     * çekilir (bkz. Main'deki batch launcher'lar).
     */
    private void handleVelzonKnowledgeBaseApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonKnowledgeBaseClient == null) {
            sendError(ex, 503, "Velzon knowledge base not configured");
            return;
        }
        if (seg.length == 4 && "articles".equals(seg[3])) {
            requireGet(ex, method, () -> sendJson(ex, 200, velzonKbArticlesJson()));
            return;
        }
        sendError(ex, 404, "Unknown resource");
    }

    private String velzonKbArticlesJson() {
        List<Map<String, String>> rows = cachedVelzonArticles().stream()
                .map(a -> Map.of("title", a.title(), "path", a.path()))
                .toList();
        return gson.toJson(rows);
    }

    private synchronized List<com.videogenerator.velzon.VelzonKnowledgeBaseClient.Article>
            cachedVelzonArticles() {
        if (cachedVelzonArticles == null) {
            cachedVelzonArticles = unchecked(velzonKnowledgeBaseClient::listArticles);
        }
        return cachedVelzonArticles;
    }

    // ==================== Velzon (X/Twitter) ====================

    private void handleVelzonApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonService == null) {
            sendError(ex, 503, "Velzon not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                switch (seg[3]) {
                    case "batches" -> requireGet(ex, method, () ->
                            sendJson(ex, 200, gson.toJson(unchecked(velzonService::listBatches))));
                    case "generate" -> requirePost(ex, method, () -> generateVelzonBatch(ex));
                    default -> sendError(ex, 404, "Unknown resource");
                }
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"tweets".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishVelzonTweet(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private void generateVelzonBatch(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String articlePath = body.has("articlePath") ? body.get("articlePath").getAsString() : null;
        if (articlePath == null || articlePath.isBlank()) {
            throw new IllegalArgumentException("articlePath is required");
        }
        int count = body.has("count") ? body.get("count").getAsInt() : 5;
        if (velzonGenerator == null) {
            sendError(ex, 503, "Velzon generator not configured");
            return;
        }
        velzonGenerator.launch(articlePath, count);
        sendJson(ex, 202, "{\"queued\":true}");
    }

    private void publishVelzonTweet(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid tweet index");
        }
        var updated = unchecked(() -> velzonService.publishTweet(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    // ==================== Velzon Instagram ====================

    private void handleVelzonInstagramApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonInstagramService == null) {
            sendError(ex, 503, "Velzon Instagram not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                switch (seg[3]) {
                    case "batches" -> requireGet(ex, method, () -> sendJson(ex, 200,
                            gson.toJson(unchecked(velzonInstagramService::listBatches))));
                    case "generate" -> requirePost(ex, method, () -> generateVelzonInstagramBatch(ex));
                    default -> sendError(ex, 404, "Unknown resource");
                }
            }
            case 7 -> {
                if (!"batches".equals(seg[3]) || !"images".equals(seg[5])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requireGet(ex, method, () -> serveVelzonInstagramImage(ex, seg[4], seg[6]));
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"posts".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishVelzonInstagramPost(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private void generateVelzonInstagramBatch(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String articlePath = body.has("articlePath") ? body.get("articlePath").getAsString() : null;
        if (articlePath == null || articlePath.isBlank()) {
            throw new IllegalArgumentException("articlePath is required");
        }
        int count = body.has("count") ? body.get("count").getAsInt() : 5;
        if (velzonInstagramGenerator == null) {
            sendError(ex, 503, "Velzon Instagram generator not configured");
            return;
        }
        velzonInstagramGenerator.launch(articlePath, count);
        sendJson(ex, 202, "{\"queued\":true}");
    }

    private void publishVelzonInstagramPost(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid post index");
        }
        var updated = unchecked(() -> velzonInstagramService.publishPost(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    private void serveVelzonInstagramImage(HttpExchange ex, String batchId, String file)
            throws IOException {
        java.nio.file.Path path = unchecked(
                () -> velzonInstagramService.imageFile(batchId, file));
        if (!java.nio.file.Files.exists(path)) {
            sendError(ex, 404, "Image not found");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== Velzon Facebook ====================

    private void handleVelzonFacebookApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonFacebookService == null) {
            sendError(ex, 503, "Velzon Facebook not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                switch (seg[3]) {
                    case "batches" -> requireGet(ex, method, () -> sendJson(ex, 200,
                            gson.toJson(unchecked(velzonFacebookService::listBatches))));
                    case "generate" -> requirePost(ex, method, () -> generateVelzonFacebookBatch(ex));
                    default -> sendError(ex, 404, "Unknown resource");
                }
            }
            case 7 -> {
                if (!"batches".equals(seg[3]) || !"images".equals(seg[5])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requireGet(ex, method, () -> serveVelzonFacebookImage(ex, seg[4], seg[6]));
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"posts".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishVelzonFacebookPost(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private void generateVelzonFacebookBatch(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String articlePath = body.has("articlePath") ? body.get("articlePath").getAsString() : null;
        if (articlePath == null || articlePath.isBlank()) {
            throw new IllegalArgumentException("articlePath is required");
        }
        int count = body.has("count") ? body.get("count").getAsInt() : 5;
        if (velzonFacebookGenerator == null) {
            sendError(ex, 503, "Velzon Facebook generator not configured");
            return;
        }
        velzonFacebookGenerator.launch(articlePath, count);
        sendJson(ex, 202, "{\"queued\":true}");
    }

    private void publishVelzonFacebookPost(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid post index");
        }
        var updated = unchecked(() -> velzonFacebookService.publishPost(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    private void serveVelzonFacebookImage(HttpExchange ex, String batchId, String file)
            throws IOException {
        java.nio.file.Path path = unchecked(
                () -> velzonFacebookService.imageFile(batchId, file));
        if (!java.nio.file.Files.exists(path)) {
            sendError(ex, 404, "Image not found");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== Velzon YouTube ====================

    /**
     * NOT: Instagram/Pinterest'in aksine burada bir "images/{file}" servis
     * rotası YOK — VelzonYoutubeScriptGenerator parti üretiminde görsel
     * ÜRETMEZ (bkz. o sınıfın javadoc'u); arkaplan görseli yalnızca
     * "Yayınla" tıklandığında, VelzonYoutubeVideoBuilder tarafından üretilir.
     * Bu yüzden inceleme ekranında yalnızca metin (anlatım/başlık/açıklama)
     * gösterilir, yayınlandıktan sonra ise doğrulama için YouTube linkine
     * gidilir.
     */
    private void handleVelzonYoutubeApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonYoutubeService == null) {
            sendError(ex, 503, "Velzon YouTube not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                switch (seg[3]) {
                    case "batches" -> requireGet(ex, method, () -> sendJson(ex, 200,
                            gson.toJson(unchecked(velzonYoutubeService::listBatches))));
                    case "generate" -> requirePost(ex, method, () -> generateVelzonYoutubeBatch(ex));
                    default -> sendError(ex, 404, "Unknown resource");
                }
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"scripts".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishVelzonYoutubeVideo(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private void generateVelzonYoutubeBatch(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String articlePath = body.has("articlePath") ? body.get("articlePath").getAsString() : null;
        if (articlePath == null || articlePath.isBlank()) {
            throw new IllegalArgumentException("articlePath is required");
        }
        int count = body.has("count") ? body.get("count").getAsInt() : 5;
        if (velzonYoutubeGenerator == null) {
            sendError(ex, 503, "Velzon YouTube generator not configured");
            return;
        }
        velzonYoutubeGenerator.launch(articlePath, count);
        sendJson(ex, 202, "{\"queued\":true}");
    }

    private void publishVelzonYoutubeVideo(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid script index");
        }
        var updated = unchecked(() -> velzonYoutubeService.publishVideo(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    // ==================== Velzon TikTok ====================

    /**
     * "generate" rotası yok — VelzonTiktokPublishService kendi video
     * üretmez, Velzon YouTube batch'inin zaten render ettiği video-XX.mp4'ü
     * ikinci bir hesaba (@velzon_tr) postlar (bkz. sınıfın javadoc'u).
     */
    private void handleVelzonTiktokApi(HttpExchange ex, String method, String[] seg)
            throws IOException {
        if (velzonTiktokService == null) {
            sendError(ex, 503, "Velzon TikTok not configured");
            return;
        }
        switch (seg.length) {
            case 4 -> {
                if (!"batches".equals(seg[3])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requireGet(ex, method, () -> sendJson(ex, 200,
                        gson.toJson(unchecked(velzonTiktokService::listBatches))));
            }
            case 8 -> {
                if (!"batches".equals(seg[3]) || !"scripts".equals(seg[5])
                        || !"publish".equals(seg[7])) {
                    sendError(ex, 404, "Unknown resource");
                    return;
                }
                requirePost(ex, method, () -> publishVelzonTiktokVideo(ex, seg[4], seg[6]));
            }
            default -> sendError(ex, 404, "Unknown resource");
        }
    }

    private void publishVelzonTiktokVideo(HttpExchange ex, String batchId, String indexStr)
            throws IOException {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid script index");
        }
        var updated = unchecked(() -> velzonTiktokService.publishToTiktok(batchId, index));
        sendJson(ex, 200, gson.toJson(updated));
    }

    // ==================== media ====================

    private static final java.util.regex.Pattern LANG = java.util.regex.Pattern.compile("[a-z]{2,3}");

    private void serveRender(HttpExchange ex, String jobId, String lang) throws IOException {
        if (!LANG.matcher(lang).matches()) {
            throw new IllegalArgumentException("Invalid language segment");
        }
        java.nio.file.Path file = service.jobs().dirFor(jobId)
                .resolve("renders/" + lang + ".mp4");
        if (!java.nio.file.Files.exists(file)) {
            sendError(ex, 404, "Render not found");
            return;
        }
        long length = java.nio.file.Files.size(file);
        String rangeHeader = ex.getRequestHeaders().getFirst("Range");
        ex.getResponseHeaders().set("Content-Type", "video/mp4");
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");

        if (rangeHeader == null) {
            ex.sendResponseHeaders(200, length);
            try (OutputStream os = ex.getResponseBody();
                 var in = java.nio.file.Files.newInputStream(file)) {
                in.transferTo(os);
            }
            return;
        }
        RangeSupport.ByteRange range = RangeSupport.parse(rangeHeader, length);
        if (range == null) {
            ex.getResponseHeaders().set("Content-Range", "bytes */" + length);
            ex.sendResponseHeaders(416, -1);
            return;
        }
        ex.getResponseHeaders().set("Content-Range",
                "bytes " + range.start() + "-" + range.end() + "/" + length);
        ex.sendResponseHeaders(206, range.length());
        try (OutputStream os = ex.getResponseBody();
             var raf = java.nio.channels.FileChannel.open(file)) {
            raf.position(range.start());
            long remaining = range.length();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(64 * 1024);
            while (remaining > 0) {
                buf.clear();
                buf.limit((int) Math.min(buf.capacity(), remaining));
                int read = raf.read(buf);
                if (read < 0) {
                    break;
                }
                os.write(buf.array(), 0, read);
                remaining -= read;
            }
        }
    }

    /** GET = profil JSON'u, PATCH = whitelist'li alan güncellemesi. */
    private void handleChannel(HttpExchange ex, String method, String channelId)
            throws IOException {
        switch (method) {
            case "GET" -> sendJson(ex, 200, gson.toJson(service.channels().load(channelId)));
            case "PATCH" -> {
                com.google.gson.JsonObject patch = gson.fromJson(
                        new String(ex.getRequestBody().readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                if (patch == null) {
                    sendError(ex, 400, "Empty patch body");
                    return;
                }
                sendJson(ex, 200, gson.toJson(service.channels().update(channelId, patch)));
            }
            default -> sendError(ex, 405, "Method not allowed");
        }
    }

    private void serveScene(HttpExchange ex, String jobId, String sceneNo) throws IOException {
        int n;
        try {
            n = Integer.parseInt(sceneNo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Scene number must be an integer");
        }
        if (n < 1 || n > 99) {
            throw new IllegalArgumentException("Scene number out of range");
        }
        java.nio.file.Path dir = service.jobs().dirFor(jobId);
        // Eski format: 01.png — Format 2.0 (sahne başına çoklu görsel): 01a.png
        java.nio.file.Path file = dir.resolve(String.format("scenes/%02d.png", n));
        if (!java.nio.file.Files.exists(file)) {
            file = dir.resolve(String.format("scenes/%02da.png", n));
        }
        if (!java.nio.file.Files.exists(file)) {
            sendError(ex, 404, "Scene not found");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== static UI ====================

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "png", "image/png",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    private void handleStatic(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                sendError(ex, 405, "Method not allowed");
                return;
            }
            String rawPath = ex.getRequestURI().getRawPath();
            if (rawPath.contains("..") || rawPath.contains("%")) {
                sendError(ex, 400, "Invalid path");
                return;
            }
            String path = rawPath.equals("/") ? "/index.html" : rawPath;
            String ext = path.contains(".")
                    ? path.substring(path.lastIndexOf('.') + 1) : "";
            String contentType = CONTENT_TYPES.get(ext);
            if (contentType == null) {
                sendError(ex, 404, "Not found");
                return;
            }
            try (var in = getClass().getResourceAsStream("/web" + path)) {
                if (in == null) {
                    sendError(ex, 404, "Not found");
                    return;
                }
                byte[] bytes = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", contentType);
                // Statik dosyalar jar içinde gömülü, deploy'da her zaman değişebilir —
                // tarayıcı VE Cloudflare edge cache'i asla eskisini tutmasın diye
                // no-store. Bu tek-operatörlü internal tool için performans kaybı
                // önemsiz, cache-yüzünden-eski-UI hatası çok daha maliyetli.
                ex.getResponseHeaders().set("Cache-Control", "no-store, must-revalidate");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } finally {
            ex.close();
        }
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
