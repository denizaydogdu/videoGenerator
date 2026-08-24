package com.videogenerator.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Meta'nın (Instagram/Facebook) postlamadan önce kimlik bilgisi olmadan
 * çektiği dört görsel servis route'unun auth muafiyeti — 2026-08-24 canlı
 * regresyonu için (bkz. BackofficeServer.start() javadoc'u). Bu route'lar
 * yanlışlıkla auth'a takılırsa Instagram/Facebook postları sessizce
 * başarısız olur ("medya indirilemedi").
 */
class BackofficePublicImageRouteTest {

    private static String[] seg(String path) {
        return path.split("/");
    }

    @Test
    void velzonAiBriefingImageRouteIsPublic() {
        assertTrue(BackofficeServer.isPublicImageRoute(
                seg("/api/velzon-ai-briefing/images/job-123/image.png")));
    }

    @Test
    void pinterestImageRouteIsNotPublic() {
        // Pinterest görseli base64 olarak media_source JSON'ına gömüyor
        // (PinterestApiClient.createPin) — Pinterest'in sunucuları bu URL'i
        // hiç çekmiyor, sadece backoffice UI'daki insan önizlemesi için var,
        // o da zaten "/" context'inin Authenticator'ıyla korunuyor.
        assertFalse(BackofficeServer.isPublicImageRoute(
                seg("/api/pinterest/batches/batch-1/images/pin-01.png")));
    }

    @Test
    void velzonInstagramImageRouteIsPublic() {
        assertTrue(BackofficeServer.isPublicImageRoute(
                seg("/api/velzon-instagram/batches/batch-1/images/post-01.png")));
    }

    @Test
    void velzonFacebookImageRouteIsPublic() {
        assertTrue(BackofficeServer.isPublicImageRoute(
                seg("/api/velzon-facebook/batches/batch-1/images/post-01.png")));
    }

    @Test
    void siblingRoutesUnderSamePrefixAreNotPublic() {
        // Aynı "batches/{id}" öneki altındaki YÖNETİM aksiyonları (liste,
        // publish) hâlâ korumalı kalmalı — sadece /images/ muaf.
        assertFalse(BackofficeServer.isPublicImageRoute(seg("/api/pinterest/batches")));
        assertFalse(BackofficeServer.isPublicImageRoute(
                seg("/api/pinterest/batches/batch-1/pins/0/publish")));
        assertFalse(BackofficeServer.isPublicImageRoute(seg("/api/velzon-instagram/batches")));
        assertFalse(BackofficeServer.isPublicImageRoute(
                seg("/api/velzon-instagram/batches/batch-1/posts/0/publish")));
        assertFalse(BackofficeServer.isPublicImageRoute(seg("/api/velzon-facebook/batches")));
        assertFalse(BackofficeServer.isPublicImageRoute(
                seg("/api/velzon-ai-briefing/images/job-123")));
        assertFalse(BackofficeServer.isPublicImageRoute(seg("/api/channels")));
        assertFalse(BackofficeServer.isPublicImageRoute(seg("/api/jobs/abc/render/en")));
    }
}
