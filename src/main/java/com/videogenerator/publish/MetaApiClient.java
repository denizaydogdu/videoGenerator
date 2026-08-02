package com.videogenerator.publish;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Meta Graph API istemcisi — Instagram Reels ve Facebook Reels yayını.
 * Her iki akış da yerel dosyayı rupload.facebook.com üzerinden resumable
 * upload ile gönderir (public video URL gerekmez).
 *
 * IG akışı: container (upload_type=resumable) -> binary upload ->
 * status_code=FINISHED bekle -> media_publish -> permalink.
 * FB akışı: video_reels start (PAGE token) -> binary upload -> finish.
 */
public class MetaApiClient {
    private static final Logger logger = LoggerFactory.getLogger(MetaApiClient.class);
    private static final String GRAPH = "https://graph.facebook.com/v26.0";
    private static final int MAX_STATUS_POLLS = 60;

    /** Test edilebilirlik dikişi — gerçek impl JDK HttpClient kullanır. */
    public interface Http {
        String get(String url) throws Exception;
        String postForm(String url, Map<String, String> form) throws Exception;
        String postBinary(String url, Map<String, String> headers, Path file) throws Exception;
    }

    private final Http http;
    private final String userToken;
    private final String pageId;
    private final String igUserId;
    private final long pollDelayMs;
    private volatile String pageToken; // lazy, /me/accounts'tan

    public MetaApiClient(Http http, String userToken, String pageId,
                         String igUserId, long pollDelayMs) {
        this.http = http;
        this.userToken = userToken;
        this.pageId = pageId;
        this.igUserId = igUserId;
        this.pollDelayMs = pollDelayMs;
    }

    /**
     * rupload aralıklı olarak sahte ProcessingFailedError döndürüyor (canlı
     * gözlem 2026-08-02: aynı dosya bir denemede ret, sonrakinde kabul).
     * Meta'nın resmî tavsiyesi: başarısız yüklemede YENİ container ile tekrar.
     */
    private static final int UPLOAD_ATTEMPTS = 3;

    public String publishInstagramReel(Path video, String caption) throws Exception {
        String containerId = null;
        Exception lastUploadFailure = null;
        for (int attempt = 1; attempt <= UPLOAD_ATTEMPTS; attempt++) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("media_type", "REELS");
            form.put("upload_type", "resumable");
            form.put("caption", caption);
            form.put("access_token", userToken);
            JsonObject container = json(http.postForm(GRAPH + "/" + igUserId + "/media", form));
            String candidate = require(container, "id", "IG container");
            String uploadUri = require(container, "uri", "IG upload uri");
            try {
                uploadBinary(uploadUri, userToken, video);
                containerId = candidate;
                break;
            } catch (Exception e) {
                lastUploadFailure = e;
                logger.warn("IG upload attempt {}/{} failed: {}",
                        attempt, UPLOAD_ATTEMPTS, e.getMessage());
                if (pollDelayMs > 0 && attempt < UPLOAD_ATTEMPTS) {
                    Thread.sleep(pollDelayMs);
                }
            }
        }
        if (containerId == null) {
            throw lastUploadFailure;
        }
        awaitContainerFinished(containerId);

        Map<String, String> pub = new LinkedHashMap<>();
        pub.put("creation_id", containerId);
        pub.put("access_token", userToken);
        JsonObject media = json(http.postForm(GRAPH + "/" + igUserId + "/media_publish", pub));
        String mediaId = require(media, "id", "IG media");

        JsonObject link = json(http.get(GRAPH + "/" + mediaId
                + "?fields=permalink&access_token=" + userToken));
        String url = link.has("permalink") ? link.get("permalink").getAsString()
                : "https://www.instagram.com/";
        logger.info("Instagram Reel published: {}", url);
        return url;
    }

    public String publishFacebookReel(Path video, String description) throws Exception {
        String token = pageToken();
        String videoId = null;
        Exception lastUploadFailure = null;
        for (int attempt = 1; attempt <= UPLOAD_ATTEMPTS; attempt++) {
            Map<String, String> start = new LinkedHashMap<>();
            start.put("upload_phase", "start");
            start.put("access_token", token);
            JsonObject session = json(http.postForm(GRAPH + "/" + pageId + "/video_reels", start));
            String candidate = require(session, "video_id", "FB reel session");
            String uploadUrl = require(session, "upload_url", "FB upload url");
            try {
                uploadBinary(uploadUrl, token, video);
                videoId = candidate;
                break;
            } catch (Exception e) {
                lastUploadFailure = e;
                logger.warn("FB upload attempt {}/{} failed: {}",
                        attempt, UPLOAD_ATTEMPTS, e.getMessage());
                if (pollDelayMs > 0 && attempt < UPLOAD_ATTEMPTS) {
                    Thread.sleep(pollDelayMs);
                }
            }
        }
        if (videoId == null) {
            throw lastUploadFailure;
        }

        Map<String, String> finish = new LinkedHashMap<>();
        finish.put("upload_phase", "finish");
        finish.put("video_id", videoId);
        finish.put("video_state", "PUBLISHED");
        finish.put("description", description);
        finish.put("access_token", token);
        json(http.postForm(GRAPH + "/" + pageId + "/video_reels", finish));

        String url = "https://www.facebook.com/reel/" + videoId;
        logger.info("Facebook Reel published: {}", url);
        return url;
    }

    /** Permalink'ten IG medya id'sini bulup "views" metriğini döner; yoksa null. */
    public Long igViewsByPermalink(String permalink) throws Exception {
        JsonObject media = json(http.get(GRAPH + "/" + igUserId
                + "/media?fields=id,permalink&limit=50&access_token=" + userToken));
        if (!media.has("data")) {
            return null;
        }
        for (var el : media.getAsJsonArray("data")) {
            JsonObject m = el.getAsJsonObject();
            if (m.has("permalink") && permalink.equals(m.get("permalink").getAsString())) {
                JsonObject insights = json(http.get(GRAPH + "/" + m.get("id").getAsString()
                        + "/insights?metric=views&access_token=" + userToken));
                return firstMetricValue(insights, "views");
            }
        }
        return null;
    }

    /**
     * FB Reel izlenmesi. Video düğümündeki "views" alanı sayfa token'ıyla
     * ekstra izin gerektirmez (video_insights read_insights isterdi).
     */
    public Long fbReelViews(String videoId) throws Exception {
        JsonObject video = json(http.get(GRAPH + "/" + videoId
                + "?fields=views&access_token=" + pageToken()));
        return video.has("views") ? video.get("views").getAsLong() : null;
    }

    private static Long firstMetricValue(JsonObject insights, String metric) {
        if (!insights.has("data")) {
            return null;
        }
        for (var el : insights.getAsJsonArray("data")) {
            JsonObject m = el.getAsJsonObject();
            if (metric.equals(m.get("name").getAsString()) && m.has("values")) {
                var values = m.getAsJsonArray("values");
                if (!values.isEmpty()) {
                    return values.get(0).getAsJsonObject().get("value").getAsLong();
                }
            }
        }
        return null;
    }

    private void uploadBinary(String uploadUrl, String token, Path video) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "OAuth " + token);
        headers.put("offset", "0");
        headers.put("file_size", String.valueOf(Files.size(video)));
        http.postBinary(uploadUrl, headers, video);
    }

    private void awaitContainerFinished(String containerId) throws Exception {
        for (int i = 0; i < MAX_STATUS_POLLS; i++) {
            JsonObject status = json(http.get(GRAPH + "/" + containerId
                    + "?fields=status_code&access_token=" + userToken));
            String code = status.has("status_code")
                    ? status.get("status_code").getAsString() : "UNKNOWN";
            switch (code) {
                case "FINISHED" -> { return; }
                case "ERROR", "EXPIRED" -> throw new IllegalStateException(
                        "IG container " + containerId + " failed: " + code);
                default -> { /* IN_PROGRESS — bekle */ }
            }
            if (pollDelayMs > 0) {
                Thread.sleep(pollDelayMs);
            }
        }
        throw new IllegalStateException("IG container " + containerId
                + " not FINISHED after " + MAX_STATUS_POLLS + " polls");
    }

    private synchronized String pageToken() throws Exception {
        if (pageToken != null) {
            return pageToken;
        }
        JsonObject resp = json(http.get(GRAPH + "/me/accounts?access_token=" + userToken));
        if (resp.has("data")) {
            for (var el : resp.getAsJsonArray("data")) {
                JsonObject page = el.getAsJsonObject();
                if (pageId.equals(page.get("id").getAsString())
                        && page.has("access_token")) {
                    pageToken = page.get("access_token").getAsString();
                    return pageToken;
                }
            }
        }
        throw new IllegalStateException(
                "Page " + pageId + " not found in /me/accounts (token izinleri?)");
    }

    private static JsonObject json(String raw) {
        JsonObject o = new Gson().fromJson(raw, JsonObject.class);
        if (o == null) {
            throw new IllegalStateException("Empty Meta API response");
        }
        if (o.has("error")) {
            throw new IllegalStateException("Meta API error: " + o.get("error"));
        }
        return o;
    }

    private static String require(JsonObject o, String field, String what) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            throw new IllegalStateException(what + " response missing '" + field + "': " + o);
        }
        return o.get(field).getAsString();
    }
}
