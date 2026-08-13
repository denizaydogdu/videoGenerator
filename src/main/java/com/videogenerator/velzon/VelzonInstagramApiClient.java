package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Instagram Content Publishing API istemcisi ("Instagram API with Instagram
 * Login" — Facebook Page GEREKTİRMEZ, MetaApiClient'ın IG akışından farklı).
 * Token zaten config'te uzun ömürlü ve doğrulanmış — Pinterest/X'in aksine
 * burada OAuth exchange akışı YOK, bu istemci sadece yayın adımlarını yapar.
 *
 * Üç adım ayrı metotlara bölündü (createMediaContainer / publishContainer /
 * getPermalink) — Pinterest'in tek createPin()'inin aksine, her adım kendi
 * HTTP isteği ve kendi hata modunu taşıyor; orkestrasyon
 * VelzonInstagramPublishService'te, testte her adımın hatası ayrı ayrı
 * ayırt edilebilsin diye.
 *
 * ÖNEMLİ: image_url PUBLİK olarak erişilebilir olmalı (Pinterest'in
 * image_base64 inline yükünün aksine) — Instagram sunucuları bu URL'i
 * kendisi indirir. Bu istemci URL'i almaz, üretmez; çağıran taraf
 * (VelzonInstagramPublishService) public base URL + backoffice image
 * endpoint'inden inşa eder.
 */
public class VelzonInstagramApiClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonInstagramApiClient.class);
    private static final String GRAPH = "https://graph.instagram.com/v23.0";

    // Canlı bulgu (2026-08-12): container oluşturulduktan hemen sonra publish
    // denenirse Instagram "Media ID is not available" (subcode 2207027) hatası
    // veriyor — container'ın kendi tarafında işlenmesi birkaç saniye sürebiliyor.
    // Meta'nın resmi çözümü: status_code=FINISHED olana kadar polling.
    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final long POLL_INTERVAL_MS = 2000;

    /** Test edilebilirlik dikişi — gerçek impl JDK HttpClient kullanır (VelzonInstagramHttp). */
    public interface Http {
        String postForm(String url, Map<String, String> form) throws Exception;
        String get(String url) throws Exception;
    }

    /** waitUntilContainerReady()'nin polling aralarındaki bekleme adımı — testte no-op enjekte edilir. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Http http;
    private final String igUserId;
    private final String accessToken;
    private final Sleeper sleeper;

    public VelzonInstagramApiClient(Http http, String igUserId, String accessToken) {
        this(http, igUserId, accessToken, Thread::sleep);
    }

    public VelzonInstagramApiClient(Http http, String igUserId, String accessToken, Sleeper sleeper) {
        this.http = http;
        this.igUserId = igUserId;
        this.accessToken = accessToken;
        this.sleeper = sleeper;
    }

    /** Adım 1: medya container oluşturur, creation id döner. */
    public String createMediaContainer(String imageUrl, String caption) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("image_url", imageUrl);
        form.put("caption", caption);
        form.put("access_token", accessToken);
        JsonObject resp = json(http.postForm(GRAPH + "/" + igUserId + "/media", form));
        String id = require(resp, "id", "IG media container");
        logger.info("IG media container created: {}", id);
        return id;
    }

    /**
     * Adım 1.5 (opsiyonel ama önerilir): container'ın Instagram tarafında
     * işlenmesini bekler. status_code FINISHED olunca döner; ERROR/EXPIRED
     * gelirse ya da MAX_POLL_ATTEMPTS aşılırsa fırlatır.
     */
    public void waitUntilContainerReady(String creationId) throws Exception {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            String status = getContainerStatus(creationId);
            if ("FINISHED".equals(status)) {
                return;
            }
            if ("ERROR".equals(status) || "EXPIRED".equals(status)) {
                throw new IllegalStateException(
                        "Instagram media container " + creationId + " status is " + status
                                + " — cannot publish");
            }
            if (attempt == MAX_POLL_ATTEMPTS) {
                throw new IllegalStateException(
                        "Instagram media container " + creationId + " still not ready after "
                                + MAX_POLL_ATTEMPTS + " attempts (last status: " + status + ")");
            }
            logger.info("IG container {} not ready yet ({}), retrying in {}ms",
                    creationId, status, POLL_INTERVAL_MS);
            sleeper.sleep(POLL_INTERVAL_MS);
        }
    }

    private String getContainerStatus(String creationId) throws Exception {
        JsonObject resp = json(http.get(GRAPH + "/" + creationId
                + "?fields=status_code&access_token=" + accessToken));
        return require(resp, "status_code", "IG container status");
    }

    /** Adım 2: container'ı yayınlar, yayınlanmış medya id'sini döner. */
    public String publishContainer(String creationId) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("creation_id", creationId);
        form.put("access_token", accessToken);
        JsonObject resp = json(http.postForm(GRAPH + "/" + igUserId + "/media_publish", form));
        String mediaId = require(resp, "id", "IG media publish");
        logger.info("IG media published: {}", mediaId);
        return mediaId;
    }

    /** Adım 3: yayınlanmış medyanın kalıcı public URL'ini döner. */
    public String getPermalink(String mediaId) throws Exception {
        JsonObject resp = json(http.get(GRAPH + "/" + mediaId
                + "?fields=permalink&access_token=" + accessToken));
        return require(resp, "permalink", "IG permalink");
    }

    private static JsonObject json(String raw) {
        JsonObject o;
        try {
            o = new Gson().fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed Instagram API response: " + raw, e);
        }
        if (o == null) {
            throw new IllegalStateException("Empty Instagram API response");
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
