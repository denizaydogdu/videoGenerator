package com.videogenerator.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetaApiClientTest {

    /** Sahte transport — çağrıları kaydeder, senaryoya göre cevap döner. */
    static class FakeHttp implements MetaApiClient.Http {
        final List<String> calls = new ArrayList<>();
        int igStatusPolls = 0;

        @Override
        public String get(String url) {
            calls.add("GET " + url);
            if (url.contains("/me/accounts")) {
                return """
                    {"data":[{"id":"PAGE1","name":"Unsolved Files","access_token":"PAGETOK"}]}""";
            }
            if (url.contains("fields=status_code")) {
                igStatusPolls++;
                return igStatusPolls < 2
                        ? "{\"status_code\":\"IN_PROGRESS\"}"
                        : "{\"status_code\":\"FINISHED\"}";
            }
            if (url.contains("fields=permalink")) {
                return "{\"permalink\":\"https://www.instagram.com/reel/ABC/\"}";
            }
            throw new AssertionError("unexpected GET " + url);
        }

        @Override
        public String postForm(String url, Map<String, String> form) {
            calls.add("POST " + url + " " + new java.util.TreeMap<>(form));
            if (url.endsWith("/IGUSER/media")) {
                assertEquals("REELS", form.get("media_type"));
                assertEquals("resumable", form.get("upload_type"));
                return "{\"id\":\"CONT1\",\"uri\":\"https://rupload.facebook.com/ig-api-upload/v26.0/CONT1\"}";
            }
            if (url.endsWith("/IGUSER/media_publish")) {
                assertEquals("CONT1", form.get("creation_id"));
                return "{\"id\":\"MEDIA1\"}";
            }
            if (url.endsWith("/PAGE1/video_reels") && "start".equals(form.get("upload_phase"))) {
                assertEquals("PAGETOK", form.get("access_token"));
                return "{\"video_id\":\"VID1\",\"upload_url\":\"https://rupload.facebook.com/video-upload/v26.0/VID1\"}";
            }
            if (url.endsWith("/PAGE1/video_reels") && "finish".equals(form.get("upload_phase"))) {
                assertEquals("PUBLISHED", form.get("video_state"));
                assertEquals("desc", form.get("description"));
                return "{\"success\":true}";
            }
            throw new AssertionError("unexpected POST " + url + " " + form);
        }

        @Override
        public String postBinary(String url, Map<String, String> headers, Path file) {
            calls.add("BIN " + url + " offset=" + headers.get("offset"));
            assertTrue(Files.exists(file));
            assertEquals("0", headers.get("offset"));
            assertNotNull(headers.get("file_size"));
            assertTrue(headers.get("Authorization").startsWith("OAuth "));
            return "{\"success\":true}";
        }
    }

    private MetaApiClient client(FakeHttp http) {
        return new MetaApiClient(http, "USERTOK", "PAGE1", "IGUSER", 0);
    }

    @Test
    void instagramReelFullFlow(@TempDir Path dir) throws Exception {
        Path video = dir.resolve("v.mp4");
        Files.writeString(video, "mp4");
        FakeHttp http = new FakeHttp();

        String url = client(http).publishInstagramReel(video, "caption #x");

        assertEquals("https://www.instagram.com/reel/ABC/", url);
        // Sıra: container -> binary upload -> status poll(lar) -> publish -> permalink
        String joined = String.join("\n", http.calls);
        assertTrue(joined.indexOf("/IGUSER/media") < joined.indexOf("BIN "));
        assertTrue(joined.indexOf("BIN ") < joined.indexOf("media_publish"));
        assertEquals(2, http.igStatusPolls, "FINISHED gelene dek poll");
    }

    @Test
    void facebookReelFullFlowUsesPageToken(@TempDir Path dir) throws Exception {
        Path video = dir.resolve("v.mp4");
        Files.writeString(video, "mp4");
        FakeHttp http = new FakeHttp();

        String url = client(http).publishFacebookReel(video, "desc");

        assertEquals("https://www.facebook.com/reel/VID1", url);
        String joined = String.join("\n", http.calls);
        // Page token /me/accounts'tan alınmalı ve start binary'den önce olmalı
        assertTrue(joined.contains("/me/accounts"));
        assertTrue(joined.indexOf("upload_phase=start") < joined.indexOf("BIN "));
    }

    @Test
    void igErrorStatusFailsInsteadOfPublishing(@TempDir Path dir) throws Exception {
        Path video = dir.resolve("v.mp4");
        Files.writeString(video, "mp4");
        FakeHttp http = new FakeHttp() {
            @Override
            public String get(String url) {
                if (url.contains("fields=status_code")) return "{\"status_code\":\"ERROR\"}";
                return super.get(url);
            }
        };

        Exception e = assertThrows(Exception.class,
                () -> client(http).publishInstagramReel(video, "c"));
        assertTrue(e.getMessage().contains("ERROR"), e.getMessage());
        assertFalse(String.join("", http.calls).contains("media_publish"),
                "hatalı container asla publish edilmemeli");
    }
}
