package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelzonBriefingClientTest {

    static class FakeHttp implements VelzonBriefingClient.Http {
        String lastUrl;
        Map<String, String> lastHeaders;
        String response = """
            {"symbol":"THYAO","timeframe":"1G","text":"TEKNİK GÖRÜNÜM\\nTest metni."}""";

        @Override
        public String get(String url, Map<String, String> headers) {
            lastUrl = url;
            lastHeaders = headers;
            return response;
        }
    }

    private VelzonBriefingClient client(FakeHttp http) {
        return new VelzonBriefingClient(http, "https://www.velzon.tr", "SERVICEKEY");
    }

    @Test
    void fetchBriefingSendsApiKeyHeaderAndReturnsText() throws Exception {
        FakeHttp http = new FakeHttp();
        VelzonBriefingClient c = client(http);

        VelzonBriefingClient.Briefing result = c.fetchBriefing("THYAO", "1G");

        assertEquals("SERVICEKEY", http.lastHeaders.get("X-API-KEY"));
        assertTrue(http.lastUrl.contains("/api/ai/symbol-briefing/service/"));
        assertTrue(http.lastUrl.contains("symbol=THYAO"));
        assertTrue(http.lastUrl.contains("tf=1G"));
        assertEquals("THYAO", result.symbol());
        assertEquals("1G", result.timeframe());
        assertTrue(result.text().contains("TEKNİK GÖRÜNÜM"));
    }

    @Test
    void fetchBriefingUrlEncodesSymbol() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = """
            {"symbol":"AK BNK","timeframe":"1G","text":"x"}""";
        VelzonBriefingClient c = client(http);

        c.fetchBriefing("AK BNK", "1G");

        assertTrue(http.lastUrl.contains("symbol=AK+BNK") || http.lastUrl.contains("symbol=AK%20BNK"));
    }

    @Test
    void throwsOnMalformedJson() {
        FakeHttp http = new FakeHttp();
        http.response = "not json";
        VelzonBriefingClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.fetchBriefing("THYAO", "1G"));
    }

    @Test
    void throwsWhenTextFieldMissing() {
        FakeHttp http = new FakeHttp();
        http.response = """
            {"symbol":"THYAO","timeframe":"1G"}""";
        VelzonBriefingClient c = client(http);

        assertThrows(IllegalStateException.class, () -> c.fetchBriefing("THYAO", "1G"));
    }

    @Test
    void propagatesHttpErrorFromTransport() {
        VelzonBriefingClient.Http failing = (url, headers) -> {
            throw new IllegalStateException("Velzon Django HTTP 429: rate_limit");
        };
        VelzonBriefingClient c = new VelzonBriefingClient(failing, "https://www.velzon.tr", "SERVICEKEY");

        Exception e = assertThrows(IllegalStateException.class, () -> c.fetchBriefing("THYAO", "1G"));
        assertTrue(e.getMessage().contains("429"));
    }
}
