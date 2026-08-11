package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * X (Twitter) API v2 istemcisi. TikTok/Pinterest'ten farkı: OAuth2
 * Authorization Code akışı PKCE zorunlu kılıyor (X'in tek desteklediği
 * grant type) — code_verifier authorizationUrl()'de üretilir, aynı
 * exchange isteğinde tekrar sunulur. Token exchange Pinterest gibi
 * HTTP Basic Auth (client_id:client_secret) kullanır.
 *
 * Canlı bulgu (2026-08-11 araştırması): Pay-Per-Use hesaplarda POST
 * /2/tweets bazı geliştiricilerde 403 veriyor — en yaygın kök sebep
 * uygulamanın bir Project'e bağlı olmaması. Kurulumda uygulama mutlaka
 * bir Project altında oluşturulmalı.
 */
public class XApiClient {
    private static final Logger logger = LoggerFactory.getLogger(XApiClient.class);
    private static final String AUTH_URL = "https://x.com/i/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.x.com/2/oauth2/token";
    private static final String TWEETS_URL = "https://api.x.com/2/tweets";
    private static final int MAX_TWEET_LEN = 280;

    public interface Http {
        String postFormBasicAuth(String url, String clientId, String clientSecret,
                                 Map<String, String> form) throws Exception;
        String postJson(String url, String bearerToken, String jsonBody) throws Exception;
    }

    private final Http http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Path tokenFile;
    private final Gson gson = new Gson();
    private String pendingCodeVerifier;

    public XApiClient(Http http, String clientId, String clientSecret,
                      String redirectUri, Path tokenFile) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenFile = tokenFile;
    }

    public String authorizationUrl(String state) {
        pendingCodeVerifier = generateCodeVerifier();
        String challenge = codeChallengeS256(pendingCodeVerifier);
        return AUTH_URL
                + "?response_type=code"
                + "&client_id=" + urlEnc(clientId)
                + "&redirect_uri=" + urlEnc(redirectUri)
                + "&scope=" + urlEnc("tweet.read tweet.write users.read offline.access")
                + "&state=" + urlEnc(state)
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256";
    }

    public void exchangeCode(String code) throws Exception {
        if (pendingCodeVerifier == null) {
            throw new IllegalStateException(
                    "authorizationUrl() must be called first — PKCE code_verifier missing");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", pendingCodeVerifier);
        JsonObject resp = gson.fromJson(
                http.postFormBasicAuth(TOKEN_URL, clientId, clientSecret, form), JsonObject.class);
        if (resp == null || !resp.has("access_token")) {
            throw new IllegalStateException("X token exchange failed: " + resp);
        }
        saveTokens(resp);
        pendingCodeVerifier = null;
        logger.info("X authorized");
    }

    private void saveTokens(JsonObject tokenResponse) throws Exception {
        tokenResponse.addProperty("saved_at_epoch_s", java.time.Instant.now().getEpochSecond());
        Files.createDirectories(tokenFile.toAbsolutePath().getParent());
        Path tmp = Files.createTempFile(tokenFile.toAbsolutePath().getParent(), "x", ".tmp");
        Files.writeString(tmp, gson.toJson(tokenResponse));
        Files.move(tmp, tokenFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonObject loadTokens() throws Exception {
        if (!Files.exists(tokenFile)) {
            throw new IllegalStateException(
                    "X not authorized yet — run 'velzon-x-auth' first");
        }
        return gson.fromJson(Files.readString(tokenFile), JsonObject.class);
    }

    String accessToken() throws Exception {
        JsonObject tokens = loadTokens();
        long savedAt = tokens.has("saved_at_epoch_s")
                ? tokens.get("saved_at_epoch_s").getAsLong() : 0;
        long expiresIn = tokens.has("expires_in") ? tokens.get("expires_in").getAsLong() : 0;
        long now = java.time.Instant.now().getEpochSecond();
        if (expiresIn > 0 && now > savedAt + expiresIn - 300) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", tokens.get("refresh_token").getAsString());
            JsonObject resp = gson.fromJson(
                    http.postFormBasicAuth(TOKEN_URL, clientId, clientSecret, form), JsonObject.class);
            if (resp == null || !resp.has("access_token")) {
                throw new IllegalStateException("X token refresh failed: " + resp);
            }
            saveTokens(resp);
            tokens = resp;
            logger.info("X access token refreshed");
        }
        return tokens.get("access_token").getAsString();
    }

    /** Tweet'i gönderir, kalıcı permalink'i döner. */
    public String postTweet(String text) throws Exception {
        if (text == null || text.isBlank() || text.length() > MAX_TWEET_LEN) {
            throw new IllegalArgumentException("Invalid tweet length: "
                    + (text == null ? 0 : text.length()) + " (max " + MAX_TWEET_LEN + ")");
        }
        String token = accessToken();
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        JsonObject resp = gson.fromJson(
                http.postJson(TWEETS_URL, token, gson.toJson(body)), JsonObject.class);
        if (resp == null || !resp.has("data")) {
            throw new IllegalStateException("X tweet post failed: " + resp);
        }
        String id = resp.getAsJsonObject("data").get("id").getAsString();
        String url = "https://x.com/i/status/" + id;
        logger.info("Tweet posted: {}", url);
        return url;
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallengeS256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String urlEnc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
