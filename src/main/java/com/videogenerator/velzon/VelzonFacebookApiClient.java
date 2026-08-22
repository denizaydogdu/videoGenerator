package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Klasik Facebook Graph API istemcisi — bir Facebook Page'e fotoğraf+metin
 * gönderisi paylaşır. Instagram'ın Content Publishing API'sinin aksine
 * (createMediaContainer -> waitUntilContainerReady -> publishContainer, üç
 * ayrı adım) burada TEK senkron çağrı var: POST /{page-id}/photos hemen
 * yayınlanmış gönderiyi döner — container yok, polling yok, ayrı publish
 * adımı yok. MetaApiClient'ın Reels akışıyla aynı GRAPH host'unu kullanır
 * (graph.facebook.com), ama bu istemci resumable video upload değil, klasik
 * tekil fotoğraf paylaşımı yapar.
 *
 * Token config'te zaten uzun ömürlü Page Access Token olarak hazır — Pinterest/
 * X'in aksine burada da OAuth exchange akışı YOK.
 */
public class VelzonFacebookApiClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonFacebookApiClient.class);
    private static final String GRAPH = "https://graph.facebook.com/v26.0";
    private static final String PERMALINK_FIELD = "permalink_url";

    /** Test edilebilirlik dikişi — gerçek impl JDK HttpClient kullanır (VelzonFacebookHttp). */
    public interface Http {
        String postForm(String url, Map<String, String> form) throws Exception;
        String get(String url) throws Exception;
    }

    private final Http http;
    private final String pageId;
    private final String pageAccessToken;

    public VelzonFacebookApiClient(Http http, String pageId, String pageAccessToken) {
        this.http = http;
        this.pageId = pageId;
        this.pageAccessToken = pageAccessToken;
    }

    /**
     * Fotoğrafı gönderi olarak paylaşır, gönderi id'sini döner. Meta'nın
     * dokümantasyonuna göre kendi sayfasının akışına yayınlanan bir fotoğraf
     * hem "id" (fotoğraf id'si) hem de "post_id" (sayfa akışındaki gönderi
     * id'si, {page-id}_{post-id} biçiminde) alanlarını döner — permalink
     * sorgusu için post_id tercih edilir, yoksa id'ye düşülür.
     */
    public String createPost(String imageUrl, String caption) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("url", imageUrl);
        form.put("caption", caption);
        form.put("access_token", pageAccessToken);
        JsonObject resp = json(http.postForm(GRAPH + "/" + pageId + "/photos", form));
        String postId = optional(resp, "post_id");
        if (postId == null) {
            postId = optional(resp, "id");
        }
        if (postId == null) {
            throw new IllegalStateException(
                    "FB photo post response missing both 'post_id' and 'id': " + resp);
        }
        logger.info("FB post created: {}", postId);
        return postId;
    }

    /** Yayınlanmış gönderinin kalıcı public URL'ini döner. */
    public String getPermalink(String postId) throws Exception {
        JsonObject resp = json(http.get(GRAPH + "/" + postId
                + "?fields=" + PERMALINK_FIELD + "&access_token=" + pageAccessToken));
        return require(resp, PERMALINK_FIELD, "FB permalink");
    }

    private static JsonObject json(String raw) {
        JsonObject o;
        try {
            o = new Gson().fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed Facebook API response: " + raw, e);
        }
        if (o == null) {
            throw new IllegalStateException("Empty Facebook API response");
        }
        return o;
    }

    private static String require(JsonObject o, String field, String what) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            throw new IllegalStateException(what + " response missing '" + field + "': " + o);
        }
        return o.get(field).getAsString();
    }

    private static String optional(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            return null;
        }
        return o.get(field).getAsString();
    }
}
