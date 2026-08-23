package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class XApiClientTest {

    static class FakeHttp implements XApiClient.Http {
        final List<String> urls = new ArrayList<>();
        final List<String> authHeaders = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        String response = "{\"data\":{\"id\":\"TWEET123\",\"text\":\"hi\"}}";

        @Override
        public String postMultipart(String url, String authorizationHeader, byte[] fileBytes,
                                    String filename) {
            throw new UnsupportedOperationException("bu testte kullanılmıyor");
        }

        @Override
        public String post(String url, String authorizationHeader, String jsonBody) {
            urls.add(url);
            authHeaders.add(authorizationHeader);
            bodies.add(jsonBody);
            return response;
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
    void postTweetSignsRequestWithOAuth1Header() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);

        c.postTweet("hello world");

        assertEquals(1, http.urls.size());
        assertEquals("https://api.x.com/2/tweets", http.urls.get(0));
        String header = http.authHeaders.get(0);
        assertTrue(header.startsWith("OAuth "), header);
        assertEquals("APIKEY", oauthParam(header, "oauth_consumer_key"));
        assertEquals("ACCESSTOKEN", oauthParam(header, "oauth_token"));
        assertEquals("HMAC-SHA1", oauthParam(header, "oauth_signature_method"));
        assertEquals("1.0", oauthParam(header, "oauth_version"));
        assertFalse(oauthParam(header, "oauth_signature").isBlank());
        assertFalse(oauthParam(header, "oauth_nonce").isBlank());
        assertFalse(oauthParam(header, "oauth_timestamp").isBlank());
    }

    @Test
    void postTweetSendsTextAsJsonBody() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);

        c.postTweet("hello world");

        assertTrue(http.bodies.get(0).contains("\"text\":\"hello world\""), http.bodies.get(0));
    }

    @Test
    void postTweetReturnsPermalink() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);

        String url = c.postTweet("hello world");

        assertEquals("https://x.com/i/status/TWEET123", url);
    }

    @Test
    void eachCallUsesAFreshNonce() throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http);

        c.postTweet("first");
        c.postTweet("second");

        String nonce1 = oauthParam(http.authHeaders.get(0), "oauth_nonce");
        String nonce2 = oauthParam(http.authHeaders.get(1), "oauth_nonce");
        assertNotEquals(nonce1, nonce2);
    }

    @Test
    void rejectsTweetOver280Chars() {
        XApiClient c = client(new FakeHttp());
        String tooLong = "x".repeat(281);

        assertThrows(IllegalArgumentException.class, () -> c.postTweet(tooLong));
    }

    @Test
    void rejectsBlankTweet() {
        XApiClient c = client(new FakeHttp());

        assertThrows(IllegalArgumentException.class, () -> c.postTweet(""));
        assertThrows(IllegalArgumentException.class, () -> c.postTweet(null));
    }

    @Test
    void throwsWhenResponseHasNoData() {
        FakeHttp http = new FakeHttp();
        http.response = "{\"errors\":[{\"message\":\"boom\"}]}";
        XApiClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.postTweet("hello"));
    }
}
