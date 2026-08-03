package com.videogenerator.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TikTokApiClientTest {

    static class FakeHttp implements TikTokApiClient.Http {
        final List<String> calls = new ArrayList<>();
        int statusPolls = 0;

        @Override
        public String postForm(String url, Map<String, String> form) {
            calls.add("FORM " + url);
            if (url.contains("/oauth/token/")) {
                assertEquals("authorization_code", form.get("grant_type"));
                return """
                    {"access_token":"AT1","refresh_token":"RT1","expires_in":86400,
                     "open_id":"OPEN1","scope":"user.info.basic,video.publish"}""";
            }
            throw new AssertionError("unexpected form POST " + url);
        }

        @Override
        public String postJson(String url, String bearer, String body) {
            calls.add("JSON " + url);
            assertEquals("AT1", bearer);
            if (url.contains("/creator_info/query/")) {
                return """
                    {"data":{"creator_username":"unsolvedfiles007",
                             "privacy_level_options":["SELF_ONLY","PUBLIC_TO_EVERYONE"]},
                     "error":{"code":"ok"}}""";
            }
            if (url.contains("/video/init/")) {
                assertTrue(body.contains("\"source\":\"FILE_UPLOAD\""), body);
                assertTrue(body.contains("\"privacy_level\":\"SELF_ONLY\""), body);
                assertTrue(body.contains("\"total_chunk_count\":1"), body);
                return """
                    {"data":{"publish_id":"PUB1",
                             "upload_url":"https://upload.tiktokapis.com/video/?upload_id=U1"},
                     "error":{"code":"ok"}}""";
            }
            if (url.contains("/status/fetch/")) {
                assertTrue(body.contains("PUB1"));
                statusPolls++;
                return statusPolls < 2
                        ? "{\"data\":{\"status\":\"PROCESSING_UPLOAD\"},\"error\":{\"code\":\"ok\"}}"
                        : "{\"data\":{\"status\":\"PUBLISH_COMPLETE\"},\"error\":{\"code\":\"ok\"}}";
            }
            throw new AssertionError("unexpected JSON POST " + url);
        }

        @Override
        public String putChunk(String url, String contentRange, Path file) {
            calls.add("PUT " + url + " range=" + contentRange);
            assertTrue(contentRange.startsWith("bytes 0-"));
            return "";
        }
    }

    @Test
    void exchangeCodeStoresTokens(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        TikTokApiClient client = new TikTokApiClient(http, "KEY", "SECRET",
                "https://cb/", dir.resolve("tok.json"), 0);

        client.exchangeCode("AUTHCODE");

        assertTrue(Files.exists(dir.resolve("tok.json")));
        String saved = Files.readString(dir.resolve("tok.json"));
        assertTrue(saved.contains("AT1") && saved.contains("RT1"));
    }

    @Test
    void directPostFullFlow(@TempDir Path dir) throws Exception {
        Path video = dir.resolve("v.mp4");
        Files.writeString(video, "mp4data");
        FakeHttp http = new FakeHttp();
        TikTokApiClient client = new TikTokApiClient(http, "KEY", "SECRET",
                "https://cb/", dir.resolve("tok.json"), 0);
        client.exchangeCode("AUTHCODE");

        String publishId = client.directPost(video, "Zodiac case #truecrime", "SELF_ONLY");

        assertEquals("PUB1", publishId);
        String joined = String.join("\n", http.calls);
        // Sıra: creator_info -> init -> upload -> status
        assertTrue(joined.indexOf("creator_info") < joined.indexOf("video/init"));
        assertTrue(joined.indexOf("video/init") < joined.indexOf("PUT "));
        assertTrue(joined.indexOf("PUT ") < joined.indexOf("status/fetch"));
        assertEquals(2, http.statusPolls, "PUBLISH_COMPLETE gelene dek poll");
    }

    @Test
    void failedStatusThrows(@TempDir Path dir) throws Exception {
        Path video = dir.resolve("v.mp4");
        Files.writeString(video, "mp4data");
        FakeHttp http = new FakeHttp() {
            @Override
            public String postJson(String url, String bearer, String body) {
                if (url.contains("/status/fetch/")) {
                    return "{\"data\":{\"status\":\"FAILED\",\"fail_reason\":\"bad_video\"},"
                            + "\"error\":{\"code\":\"ok\"}}";
                }
                return super.postJson(url, bearer, body);
            }
        };
        TikTokApiClient client = new TikTokApiClient(http, "KEY", "SECRET",
                "https://cb/", dir.resolve("tok.json"), 0);
        client.exchangeCode("AUTHCODE");

        Exception e = assertThrows(Exception.class,
                () -> client.directPost(video, "t", "SELF_ONLY"));
        assertTrue(e.getMessage().contains("bad_video"), e.getMessage());
    }
}
