package com.videogenerator.pinterest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pinterest API v5 istemcisi. TikTok/Meta'dan farkı: görsel yükleme ayrı
 * bir upload adımı istemiyor — Create Pin isteğinde base64 gömülü gidiyor
 * (media_source.source_type=image_base64), tek istek yeterli.
 *
 * Token exchange HTTP Basic Auth (client_id:client_secret) kullanır —
 * TikTok'un form-body client_secret'ından farklı, Pinterest'in kendi
 * OAuth2 sözleşmesi.
 */
public class PinterestApiClient {
    private static final Logger logger = LoggerFactory.getLogger(PinterestApiClient.class);
    private static final String API = "https://api.pinterest.com/v5";

    public interface Http {
        String postFormBasicAuth(String url, String clientId, String clientSecret,
                                 Map<String, String> form) throws Exception;
        String postJson(String url, String bearerToken, String jsonBody) throws Exception;
        String get(String url, String bearerToken) throws Exception;
    }

    private final Http http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Path tokenFile;
    private final long pollDelayMs; // şu an kullanılmıyor, TikTok/Meta ile arayüz tutarlılığı için
    private final Gson gson = new Gson();

    public PinterestApiClient(Http http, String clientId, String clientSecret,
                              String redirectUri, Path tokenFile, long pollDelayMs) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenFile = tokenFile;
        this.pollDelayMs = pollDelayMs;
    }

    public String authorizationUrl(String state) {
        return "https://www.pinterest.com/oauth/"
                + "?client_id=" + clientId
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri,
                        java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + java.net.URLEncoder.encode(
                        "pins:read,pins:write,boards:read,boards:write",
                        java.nio.charset.StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    public void exchangeCode(String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        JsonObject resp = gson.fromJson(
                http.postFormBasicAuth(API + "/oauth/token", clientId, clientSecret, form),
                JsonObject.class);
        if (resp == null || !resp.has("access_token")) {
            throw new IllegalStateException("Pinterest token exchange failed: " + resp);
        }
        saveTokens(resp);
        logger.info("Pinterest authorized");
    }

    private void saveTokens(JsonObject tokenResponse) throws Exception {
        tokenResponse.addProperty("saved_at_epoch_s", java.time.Instant.now().getEpochSecond());
        Files.createDirectories(tokenFile.toAbsolutePath().getParent());
        Path tmp = Files.createTempFile(tokenFile.toAbsolutePath().getParent(), "pin", ".tmp");
        Files.writeString(tmp, gson.toJson(tokenResponse));
        Files.move(tmp, tokenFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonObject loadTokens() throws Exception {
        if (!Files.exists(tokenFile)) {
            throw new IllegalStateException(
                    "Pinterest not authorized yet — run 'pinterest-auth' first");
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
                    http.postFormBasicAuth(API + "/oauth/token", clientId, clientSecret, form),
                    JsonObject.class);
            if (resp == null || !resp.has("access_token")) {
                throw new IllegalStateException("Pinterest token refresh failed: " + resp);
            }
            saveTokens(resp);
            tokens = resp;
            logger.info("Pinterest access token refreshed");
        }
        return tokens.get("access_token").getAsString();
    }

    /** Pano adına göre board_id bulur — panolar oluşturulduktan sonra çağrılır. */
    public String findBoardIdByName(String boardName) throws Exception {
        String token = accessToken();
        JsonObject resp = gson.fromJson(
                http.get(API + "/boards", token), JsonObject.class);
        if (resp != null && resp.has("items")) {
            for (var el : resp.getAsJsonArray("items")) {
                JsonObject board = el.getAsJsonObject();
                if (boardName.equals(board.get("name").getAsString())) {
                    return board.get("id").getAsString();
                }
            }
        }
        throw new IllegalStateException("Pinterest board not found: " + boardName);
    }

    /** Pin oluşturur, kalıcı pin URL'sini döner. */
    public String createPin(Path imageFile, String boardId, String title,
                            String description, String altText) throws Exception {
        String token = accessToken();
        String contentType = contentTypeFor(imageFile);
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imageFile));

        JsonObject mediaSource = new JsonObject();
        mediaSource.addProperty("source_type", "image_base64");
        mediaSource.addProperty("content_type", contentType);
        mediaSource.addProperty("data", base64);

        JsonObject body = new JsonObject();
        body.addProperty("board_id", boardId);
        body.addProperty("title", title);
        body.addProperty("description", description);
        body.addProperty("alt_text", altText);
        body.add("media_source", mediaSource);

        JsonObject resp = gson.fromJson(
                http.postJson(API + "/pins", token, gson.toJson(body)), JsonObject.class);
        if (resp == null || !resp.has("id")) {
            throw new IllegalStateException("Pinterest pin creation failed: " + resp);
        }
        String url = "https://www.pinterest.com/pin/" + resp.get("id").getAsString() + "/";
        logger.info("Pinterest pin created: {}", url);
        return url;
    }

    private static String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/png";
    }
}
