package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XApiClientTest {

    static class FakeHttp implements XApiClient.Http {
        final List<String> calls = new ArrayList<>();
        String lastCodeVerifier;

        @Override
        public String postFormBasicAuth(String url, String clientId, String clientSecret,
                                        Map<String, String> form) {
            calls.add("FORM " + url + " auth=" + clientId + ":" + clientSecret
                    + " " + new java.util.TreeMap<>(form));
            if (form.containsKey("code_verifier")) {
                lastCodeVerifier = form.get("code_verifier");
            }
            return """
                {"access_token":"AT1","refresh_token":"RT1","expires_in":7200}""";
        }

        @Override
        public String postJson(String url, String bearerToken, String jsonBody) {
            calls.add("JSON " + url + " bearer=" + bearerToken);
            if (url.endsWith("/2/tweets")) {
                assertTrue(jsonBody.contains("\"text\""), jsonBody);
                return "{\"data\":{\"id\":\"TWEET123\",\"text\":\"hi\"}}";
            }
            throw new AssertionError("unexpected JSON POST " + url);
        }
    }

    private XApiClient client(FakeHttp http, Path tokenFile) {
        return new XApiClient(http, "CID", "CSECRET", "https://cb/", tokenFile);
    }

    @Test
    void authorizationUrlIncludesPkceChallengeAndScopes() {
        XApiClient c = client(new FakeHttp(), Path.of("/dev/null/unused"));

        String url = c.authorizationUrl("state123");

        assertTrue(url.startsWith("https://x.com/i/oauth2/authorize"));
        assertTrue(url.contains("client_id=CID"));
        assertTrue(url.contains("state=state123"));
        assertTrue(url.contains("code_challenge="));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("scope=tweet.read+tweet.write+users.read+offline.access")
                || url.contains("scope=tweet.read%20tweet.write%20users.read%20offline.access"));
    }

    @Test
    void exchangeCodeSendsSameVerifierUsedInAuthorizationUrl(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http, dir.resolve("tok.json"));
        c.authorizationUrl("state123"); // code_verifier üretir, dahili tutar

        c.exchangeCode("AUTHCODE");

        assertNotNull(http.lastCodeVerifier, "token exchange code_verifier göndermeli");
        assertTrue(Files.exists(dir.resolve("tok.json")));
        String saved = Files.readString(dir.resolve("tok.json"));
        assertTrue(saved.contains("AT1") && saved.contains("RT1"));
    }

    @Test
    void exchangeCodeWithoutPriorAuthorizationUrlFails(@TempDir Path dir) {
        XApiClient c = client(new FakeHttp(), dir.resolve("tok.json"));

        assertThrows(IllegalStateException.class, () -> c.exchangeCode("AUTHCODE"));
    }

    @Test
    void postTweetReturnsPermalink(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http, dir.resolve("tok.json"));
        c.authorizationUrl("s");
        c.exchangeCode("AUTHCODE");

        String url = c.postTweet("hello world");

        assertEquals("https://x.com/i/status/TWEET123", url);
    }

    @Test
    void refreshesExpiredToken(@TempDir Path dir) throws Exception {
        Path tokenFile = dir.resolve("tok.json");
        Files.writeString(tokenFile, """
            {"access_token":"OLD","refresh_token":"RT1","expires_in":10,
             "saved_at_epoch_s":1}""");
        FakeHttp http = new FakeHttp() {
            @Override
            public String postFormBasicAuth(String url, String clientId, String clientSecret,
                                            Map<String, String> form) {
                calls.add("REFRESH " + form.get("grant_type"));
                return "{\"access_token\":\"NEW\",\"refresh_token\":\"RT1\",\"expires_in\":7200}";
            }
        };
        XApiClient c = client(http, tokenFile);

        String url = c.postTweet("hi");

        assertEquals("https://x.com/i/status/TWEET123", url);
        assertTrue(http.calls.get(0).contains("REFRESH refresh_token"));
        assertTrue(http.calls.get(1).contains("bearer=NEW"));
    }

    @Test
    void rejectsTweetOver280Chars(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        XApiClient c = client(http, dir.resolve("tok.json"));
        c.authorizationUrl("s");
        c.exchangeCode("AUTHCODE");
        String tooLong = "x".repeat(281);

        assertThrows(IllegalArgumentException.class, () -> c.postTweet(tooLong));
    }
}
