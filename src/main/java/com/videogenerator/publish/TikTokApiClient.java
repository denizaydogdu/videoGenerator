package com.videogenerator.publish;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TikTok Content Posting API istemcisi (Direct Post, FILE_UPLOAD).
 * Akış: OAuth code -> token (dosyada saklanır, refresh'lenir) ->
 * creator_info sorgusu -> video init (tek chunk, <64MB) -> PUT upload ->
 * status poll (PUBLISH_COMPLETE / FAILED).
 *
 * Sandbox notu: uygulama onaylanana dek privacy_level SELF_ONLY zorunlu —
 * post yalnız hesabın kendisine görünür.
 */
public class TikTokApiClient {
    private static final Logger logger = LoggerFactory.getLogger(TikTokApiClient.class);
    private static final String API = "https://open.tiktokapis.com/v2";
    private static final long MAX_SINGLE_CHUNK = 64L * 1024 * 1024;
    private static final int MAX_STATUS_POLLS = 60;

    public interface Http {
        String postForm(String url, Map<String, String> form) throws Exception;
        String postJson(String url, String bearerToken, String body) throws Exception;
        String putChunk(String url, String contentRange, Path file) throws Exception;
    }

    private final Http http;
    private final String clientKey;
    private final String clientSecret;
    private final String redirectUri;
    private final Path tokenFile;
    private final long pollDelayMs;
    private final Gson gson = new Gson();

    public TikTokApiClient(Http http, String clientKey, String clientSecret,
                           String redirectUri, Path tokenFile, long pollDelayMs) {
        this.http = http;
        this.clientKey = clientKey;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenFile = tokenFile;
        this.pollDelayMs = pollDelayMs;
    }

    /** Kullanıcının tarayıcıda açacağı yetkilendirme adresi. */
    public String authorizationUrl(String state) {
        return "https://www.tiktok.com/v2/auth/authorize/"
                + "?client_key=" + clientKey
                + "&scope=user.info.basic,video.publish,video.upload"
                + "&response_type=code"
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri,
                        java.nio.charset.StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    public void exchangeCode(String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_key", clientKey);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri);
        JsonObject resp = gson.fromJson(
                http.postForm(API + "/oauth/token/", form), JsonObject.class);
        if (resp == null || !resp.has("access_token")) {
            throw new IllegalStateException("TikTok token exchange failed: " + resp);
        }
        saveTokens(resp);
        logger.info("TikTok authorized (open_id: {})",
                resp.has("open_id") ? resp.get("open_id").getAsString() : "?");
    }

    private void saveTokens(JsonObject tokenResponse) throws Exception {
        tokenResponse.addProperty("saved_at_epoch_s", java.time.Instant.now().getEpochSecond());
        Files.createDirectories(tokenFile.getParent());
        Path tmp = Files.createTempFile(tokenFile.getParent(), "tt", ".tmp");
        Files.writeString(tmp, gson.toJson(tokenResponse));
        Files.move(tmp, tokenFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonObject loadTokens() throws Exception {
        if (!Files.exists(tokenFile)) {
            throw new IllegalStateException(
                    "TikTok not authorized yet — run 'tiktok-auth' first");
        }
        return gson.fromJson(Files.readString(tokenFile), JsonObject.class);
    }

    /** Süresi geçtiyse refresh_token ile yeniler; geçerli access token döner. */
    String accessToken() throws Exception {
        JsonObject tokens = loadTokens();
        long savedAt = tokens.has("saved_at_epoch_s")
                ? tokens.get("saved_at_epoch_s").getAsLong() : 0;
        long expiresIn = tokens.has("expires_in")
                ? tokens.get("expires_in").getAsLong() : 0;
        long now = java.time.Instant.now().getEpochSecond();
        if (expiresIn > 0 && now > savedAt + expiresIn - 300) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("client_key", clientKey);
            form.put("client_secret", clientSecret);
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", tokens.get("refresh_token").getAsString());
            JsonObject resp = gson.fromJson(
                    http.postForm(API + "/oauth/token/", form), JsonObject.class);
            if (resp == null || !resp.has("access_token")) {
                throw new IllegalStateException("TikTok token refresh failed: " + resp);
            }
            saveTokens(resp);
            tokens = resp;
            logger.info("TikTok access token refreshed");
        }
        return tokens.get("access_token").getAsString();
    }

    /** Direct Post: publish_id döner. privacyLevel: sandbox'ta SELF_ONLY. */
    public String directPost(Path video, String title, String privacyLevel) throws Exception {
        String token = accessToken();

        // Review kılavuzu creator_info sorgusunu şart koşuyor (UX gereği)
        JsonObject creator = data(http.postJson(
                API + "/post/publish/creator_info/query/", token, "{}"));
        logger.info("TikTok creator: {}", creator.has("creator_username")
                ? creator.get("creator_username").getAsString() : "?");

        long size = Files.size(video);
        if (size > MAX_SINGLE_CHUNK) {
            throw new IllegalStateException("Video exceeds single-chunk limit: " + size);
        }
        JsonObject postInfo = new JsonObject();
        postInfo.addProperty("title", title);
        postInfo.addProperty("privacy_level", privacyLevel);
        JsonObject sourceInfo = new JsonObject();
        sourceInfo.addProperty("source", "FILE_UPLOAD");
        sourceInfo.addProperty("video_size", size);
        sourceInfo.addProperty("chunk_size", size);
        sourceInfo.addProperty("total_chunk_count", 1);
        JsonObject init = new JsonObject();
        init.add("post_info", postInfo);
        init.add("source_info", sourceInfo);

        JsonObject initData = data(http.postJson(
                API + "/post/publish/video/init/", token, gson.toJson(init)));
        String publishId = initData.get("publish_id").getAsString();
        String uploadUrl = initData.get("upload_url").getAsString();

        http.putChunk(uploadUrl,
                "bytes 0-" + (size - 1) + "/" + size, video);

        for (int i = 0; i < MAX_STATUS_POLLS; i++) {
            JsonObject status = data(http.postJson(
                    API + "/post/publish/status/fetch/", token,
                    "{\"publish_id\":\"" + publishId + "\"}"));
            String code = status.has("status")
                    ? status.get("status").getAsString() : "UNKNOWN";
            switch (code) {
                case "PUBLISH_COMPLETE" -> {
                    logger.info("TikTok publish complete: {}", publishId);
                    return publishId;
                }
                case "FAILED" -> throw new IllegalStateException(
                        "TikTok publish failed: " + (status.has("fail_reason")
                                ? status.get("fail_reason").getAsString() : "unknown"));
                default -> { /* PROCESSING_* — bekle */ }
            }
            if (pollDelayMs > 0) {
                Thread.sleep(pollDelayMs);
            }
        }
        throw new IllegalStateException("TikTok publish not complete after "
                + MAX_STATUS_POLLS + " polls: " + publishId);
    }

    private JsonObject data(String raw) {
        JsonObject o = gson.fromJson(raw, JsonObject.class);
        if (o == null) {
            throw new IllegalStateException("Empty TikTok response");
        }
        if (o.has("error") && o.getAsJsonObject("error").has("code")
                && !"ok".equals(o.getAsJsonObject("error").get("code").getAsString())) {
            throw new IllegalStateException("TikTok API error: " + o.get("error"));
        }
        if (!o.has("data")) {
            throw new IllegalStateException("TikTok response missing data: " + o);
        }
        return o.getAsJsonObject("data");
    }
}
