package com.videogenerator.pinterest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PinterestApiClientTest {

    static class FakeHttp implements PinterestApiClient.Http {
        final List<String> calls = new ArrayList<>();

        @Override
        public String postFormBasicAuth(String url, String clientId, String clientSecret,
                                        Map<String, String> form) {
            calls.add("FORM " + url + " auth=" + clientId + ":" + clientSecret
                    + " " + new java.util.TreeMap<>(form));
            return """
                {"access_token":"AT1","refresh_token":"RT1","expires_in":2592000}""";
        }

        @Override
        public String postJson(String url, String bearerToken, String jsonBody) {
            calls.add("JSON " + url + " bearer=" + bearerToken);
            if (url.endsWith("/v5/pins")) {
                assertTrue(jsonBody.contains("\"board_id\":\"BOARD1\""), jsonBody);
                assertTrue(jsonBody.contains("\"source_type\":\"image_base64\""), jsonBody);
                assertTrue(jsonBody.contains("\"content_type\":\"image/png\""), jsonBody);
                return "{\"id\":\"PIN123\"}";
            }
            throw new AssertionError("unexpected JSON POST " + url);
        }

        @Override
        public String get(String url, String bearerToken) {
            calls.add("GET " + url + " bearer=" + bearerToken);
            if (url.contains("/v5/boards")) {
                return """
                    {"items":[
                      {"id":"BOARD_OTHER","name":"Travel"},
                      {"id":"BOARD1","name":"Small Space Living"}
                    ]}""";
            }
            throw new AssertionError("unexpected GET " + url);
        }
    }

    private PinterestApiClient client(FakeHttp http, Path tokenFile) {
        return new PinterestApiClient(http, "CID", "CSECRET",
                "https://cb/", tokenFile, 0);
    }

    @Test
    void exchangeCodeUsesBasicAuthAndStoresTokens(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        Path tokenFile = dir.resolve("tok.json");

        client(http, tokenFile).exchangeCode("AUTHCODE");

        assertTrue(Files.exists(tokenFile));
        String saved = Files.readString(tokenFile);
        assertTrue(saved.contains("AT1") && saved.contains("RT1"));
        assertTrue(http.calls.get(0).contains("auth=CID:CSECRET"));
    }

    @Test
    void findBoardIdByNameMatchesExactName(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        PinterestApiClient c = client(http, dir.resolve("tok.json"));
        c.exchangeCode("AUTHCODE");

        String id = c.findBoardIdByName("Small Space Living");

        assertEquals("BOARD1", id);
    }

    @Test
    void findBoardIdByNameThrowsWhenNotFound(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        PinterestApiClient c = client(http, dir.resolve("tok.json"));
        c.exchangeCode("AUTHCODE");

        assertThrows(IllegalStateException.class, () -> c.findBoardIdByName("Nonexistent"));
    }

    @Test
    void createPinEncodesImageAndReturnsUrl(@TempDir Path dir) throws Exception {
        Path image = dir.resolve("pin-01.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        FakeHttp http = new FakeHttp();
        PinterestApiClient c = client(http, dir.resolve("tok.json"));
        c.exchangeCode("AUTHCODE");

        String url = c.createPin(image, "BOARD1", "Title", "Description", "Alt text");

        assertEquals("https://www.pinterest.com/pin/PIN123/", url);
    }

    @Test
    void refreshesExpiredToken(@TempDir Path dir) throws Exception {
        Path tokenFile = dir.resolve("tok.json");
        // -1 saniye önce süresi bitmiş kaydedilmiş bir token yaz
        Files.writeString(tokenFile, """
            {"access_token":"OLD","refresh_token":"RT1","expires_in":10,
             "saved_at_epoch_s":1}""");
        FakeHttp http = new FakeHttp() {
            @Override
            public String postFormBasicAuth(String url, String clientId, String clientSecret,
                                            Map<String, String> form) {
                calls.add("REFRESH " + form.get("grant_type"));
                return "{\"access_token\":\"NEW\",\"refresh_token\":\"RT1\",\"expires_in\":2592000}";
            }
        };
        PinterestApiClient c = client(http, tokenFile);

        String id = c.findBoardIdByName("Small Space Living");

        assertEquals("BOARD1", id);
        assertTrue(http.calls.get(0).contains("REFRESH refresh_token"));
        assertTrue(http.calls.get(1).contains("bearer=NEW"));
    }
}
