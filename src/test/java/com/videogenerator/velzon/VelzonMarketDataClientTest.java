package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VelzonMarketDataClientTest {

    static class FakeHttp implements VelzonMarketDataClient.Http {
        final List<String> urls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        String response = "[]";

        @Override
        public String get(String url, Map<String, String> headers) {
            urls.add(url);
            this.headers.add(headers);
            return response;
        }
    }

    private VelzonMarketDataClient client(FakeHttp http) {
        return new VelzonMarketDataClient(http, "https://gate.velzon.tr", "KEY123");
    }

    @Test
    void fetchCandlesParsesStringFieldsIntoDoubles() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = """
            [{"date":"2026-08-12","open":"10.5","high":"11.0","low":"10.1",
              "close":"10.8","vol":"12345","size":"1"}]""";
        VelzonMarketDataClient c = client(http);

        List<VelzonMarketDataClient.Candle> candles = c.fetchCandles("THYAO", "1D", 1);

        assertEquals(1, candles.size());
        VelzonMarketDataClient.Candle candle = candles.get(0);
        assertEquals("2026-08-12", candle.date());
        assertEquals(10.5, candle.open());
        assertEquals(11.0, candle.high());
        assertEquals(10.1, candle.low());
        assertEquals(10.8, candle.close());
        assertEquals(12345, candle.volume());
        assertEquals(1, candle.size());
    }

    @Test
    void fetchCandlesSendsApiKeyHeaderAndUrl() throws Exception {
        FakeHttp http = new FakeHttp();
        VelzonMarketDataClient c = client(http);

        c.fetchCandles("THYAO", "1D", 30);

        assertEquals(1, http.urls.size());
        String url = http.urls.get(0);
        assertTrue(url.startsWith("https://gate.velzon.tr/api/chart/v2/THYAO"), url);
        assertTrue(url.contains("period=1D"), url);
        assertTrue(url.contains("barCount=30"), url);
        assertFalse(url.contains("CurrencyCode"), url);
        assertEquals("KEY123", http.headers.get(0).get("X-API-KEY"));
        assertEquals("application/json", http.headers.get(0).get("Content-Type"));
    }

    @Test
    void emptyArrayResponseReturnsEmptyListNotError() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = "[]";
        VelzonMarketDataClient c = client(http);

        List<VelzonMarketDataClient.Candle> candles = c.fetchCandles("THYAO", "1D", 30);

        assertTrue(candles.isEmpty());
    }

    @Test
    void fetchCandlesWithValidCurrencyAddsParam() throws Exception {
        FakeHttp http = new FakeHttp();
        VelzonMarketDataClient c = client(http);

        c.fetchCandles("XU100", "1D", 30, "usd");

        assertTrue(http.urls.get(0).contains("CurrencyCode=USD"), http.urls.get(0));
    }

    @Test
    void fetchCandlesWithInvalidCurrencyThrows() {
        VelzonMarketDataClient c = client(new FakeHttp());

        assertThrows(IllegalArgumentException.class,
                () -> c.fetchCandles("XU100", "1D", 30, "TRY"));
    }

    @Test
    void nonArrayJsonResponseThrowsClearException() {
        FakeHttp http = new FakeHttp();
        http.response = "{\"error\":\"nope\"}";
        VelzonMarketDataClient c = client(http);

        Exception e = assertThrows(IllegalStateException.class,
                () -> c.fetchCandles("THYAO", "1D", 30));
        assertTrue(e.getMessage().contains("THYAO"), e.getMessage());
    }

    @Test
    void httpErrorPropagatesFromHttpLayer() {
        VelzonMarketDataClient.Http failingHttp = (url, headers) -> {
            throw new IllegalStateException("gate HTTP 500: server error");
        };
        VelzonMarketDataClient c = new VelzonMarketDataClient(
                failingHttp, "https://gate.velzon.tr", "KEY123");

        Exception e = assertThrows(IllegalStateException.class,
                () -> c.fetchCandles("THYAO", "1D", 30));
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }
}
