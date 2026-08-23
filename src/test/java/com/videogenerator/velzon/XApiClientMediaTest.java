package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** XApiClient'ın medya (görsel) yükleme + görselli tweet atma yeteneği. */
class XApiClientMediaTest {

    static class FakeHttp implements XApiClient.Http {
        final List<String> urls = new ArrayList<>();
        final List<String> authHeaders = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<byte[]> uploadedBytes = new ArrayList<>();
        String uploadResponse = "{\"media_id_string\":\"MEDIA123\"}";
        String tweetResponse = "{\"data\":{\"id\":\"TWEET456\",\"text\":\"hi\"}}";

        @Override
        public String post(String url, String authorizationHeader, String jsonBody) {
            urls.add(url);
            authHeaders.add(authorizationHeader);
            bodies.add(jsonBody);
            return tweetResponse;
        }

        @Override
        public String postMultipart(String url, String authorizationHeader, byte[] fileBytes,
                                    String filename) {
            urls.add(url);
            authHeaders.add(authorizationHeader);
            uploadedBytes.add(fileBytes);
            return uploadResponse;
        }
    }

    private XApiClient client(FakeHttp http) {
        return new XApiClient(http, "APIKEY", "APISECRET", "ACCESSTOKEN", "ACCESSSECRET");
    }

    private static String oauthParam(String header, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(header);
        assertTrue(m.find(), "header missing " + name + ": " + header);
        return m.group(1);
    }

    @Test
    void uploadMediaPostsToUploadEndpointAndReturnsMediaId() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);
        byte[] fakeImage = "fake-png-bytes".getBytes();

        String mediaId = c.uploadMedia(fakeImage);

        assertEquals("MEDIA123", mediaId);
        assertEquals(1, http.urls.size());
        assertEquals("https://upload.twitter.com/1.1/media/upload.json", http.urls.get(0));
        assertArrayEquals(fakeImage, http.uploadedBytes.get(0));
        String header = http.authHeaders.get(0);
        assertTrue(header.startsWith("OAuth "));
        assertEquals("APIKEY", oauthParam(header, "oauth_consumer_key"));
    }

    @Test
    void uploadMediaThrowsWhenMediaIdMissing() {
        FakeHttp http = new FakeHttp();
        http.uploadResponse = "{\"errors\":[{\"message\":\"boom\"}]}";
        XApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.uploadMedia("bytes".getBytes()));
    }

    @Test
    void postTweetWithMediaIncludesMediaIdsInBody() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);

        String url = c.postTweetWithMedia("hello world", "MEDIA123");

        assertEquals("https://x.com/i/status/TWEET456", url);
        assertEquals(1, http.bodies.size());
        assertTrue(http.bodies.get(0).contains("\"media_ids\":[\"MEDIA123\"]"), http.bodies.get(0));
        assertTrue(http.bodies.get(0).contains("\"text\":\"hello world\""), http.bodies.get(0));
    }

    @Test
    void postTweetWithMediaRejectsOver280Chars() {
        XApiClient c = client(new FakeHttp());
        String tooLong = "x".repeat(281);

        assertThrows(IllegalArgumentException.class, () -> c.postTweetWithMedia(tooLong, "MEDIA123"));
    }
}
