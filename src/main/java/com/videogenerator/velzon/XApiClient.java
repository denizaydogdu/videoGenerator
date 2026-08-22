package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * X (Twitter) API v2 istemcisi — OAuth 1.0a (user-context). Velzon'un
 * kendi Django backend'inde (velzon-django, post_finansal_ozet.py) zaten
 * kullanılan ve @velzontr hesabına yetkilendirilmiş olduğu doğrulanmış
 * (curl ile GET /2/users/me -&gt; {"username":"velzontr"}) anahtarları
 * yeniden kullanır. OAuth2 PKCE'nin aksine hiçbir interaktif
 * yetkilendirme akışı GEREKMEZ — anahtarlar zaten kalıcı olarak
 * yetkilendirilmiş durumda, token dosyası/exchange/refresh yok.
 */
public class XApiClient {
    private static final Logger logger = LoggerFactory.getLogger(XApiClient.class);
    private static final String TWEETS_URL = "https://api.x.com/2/tweets";
    private static final int MAX_TWEET_LEN = 280;

    public interface Http {
        String post(String url, String authorizationHeader, String jsonBody) throws Exception;
    }

    private final Http http;
    private final String apiKey;
    private final String apiSecret;
    private final String accessToken;
    private final String accessTokenSecret;
    private final Gson gson = new Gson();

    public XApiClient(Http http, String apiKey, String apiSecret,
                      String accessToken, String accessTokenSecret) {
        this.http = http;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.accessToken = accessToken;
        this.accessTokenSecret = accessTokenSecret;
    }

    /** Tweet'i gönderir, kalıcı permalink'i döner. */
    public String postTweet(String text) throws Exception {
        if (text == null || text.isBlank() || text.length() > MAX_TWEET_LEN) {
            throw new IllegalArgumentException("Invalid tweet length: "
                    + (text == null ? 0 : text.length()) + " (max " + MAX_TWEET_LEN + ")");
        }
        String authHeader = oauth1Header("POST", TWEETS_URL);
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        JsonObject resp = gson.fromJson(
                http.post(TWEETS_URL, authHeader, gson.toJson(body)), JsonObject.class);
        if (resp == null || !resp.has("data")) {
            throw new IllegalStateException("X tweet post failed: " + resp);
        }
        String id = resp.getAsJsonObject("data").get("id").getAsString();
        String url = "https://x.com/i/status/" + id;
        logger.info("Tweet posted: {}", url);
        return url;
    }

    /**
     * OAuth 1.0a "Authorization" header'ını RFC 5849'a göre imzalar. JSON
     * gövdeli isteklerde yalnızca oauth_* parametreleri imza tabanına
     * girer (x-www-form-urlencoded gövde/query string parametrelerinin
     * aksine) — bu, X API v2'nin ve tweepy'nin kendi davranışıyla
     * tutarlıdır.
     */
    private String oauth1Header(String method, String url) throws Exception {
        TreeMap<String, String> oauthParams = new TreeMap<>();
        oauthParams.put("oauth_consumer_key", apiKey);
        oauthParams.put("oauth_nonce", UUID.randomUUID().toString().replace("-", ""));
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        oauthParams.put("oauth_token", accessToken);
        oauthParams.put("oauth_version", "1.0");

        String paramStr = oauthParams.entrySet().stream()
                .map(e -> pct(e.getKey()) + "=" + pct(e.getValue()))
                .collect(Collectors.joining("&"));
        String baseStr = method + "&" + pct(url) + "&" + pct(paramStr);
        String signingKey = pct(apiSecret) + "&" + pct(accessTokenSecret);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(baseStr.getBytes(StandardCharsets.UTF_8)));
        oauthParams.put("oauth_signature", signature);

        return "OAuth " + oauthParams.entrySet().stream()
                .map(e -> pct(e.getKey()) + "=\"" + pct(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
    }

    /** RFC 3986 percent-encode — java.net.URLEncoder form-encoding kurallarından farklı. */
    private static String pct(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
