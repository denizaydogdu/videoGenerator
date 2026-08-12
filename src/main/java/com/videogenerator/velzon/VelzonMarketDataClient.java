package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Velzon'un kendi backend'inden (gate.velzon.tr, Spring Boot — Django
 * frontend'in de aynı X-API-KEY ile doğrudan bağlandığı uç nokta) BIST
 * mum verisi çeker. Django'yu aradan çıkarır; bkz. velzon-django
 * ADR-005-frontend-apikey-proxy-public-veri.md.
 */
public class VelzonMarketDataClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonMarketDataClient.class);
    private static final Set<String> VALID_CURRENCIES = Set.of("USD", "EUR", "XU100");

    public interface Http {
        String get(String url, Map<String, String> headers) throws Exception;
    }

    /** V2 chart response alanları — backend'de String olarak dönüyor, burada double'a çevrilir. */
    public record Candle(String date, double open, double high, double low,
                          double close, double volume, double size) {}

    private final Http http;
    private final String baseUrl;
    private final String apiKey;
    private final Gson gson = new Gson();

    public VelzonMarketDataClient(Http http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public List<Candle> fetchCandles(String symbol, String period, int barCount) throws Exception {
        return fetchCandles(symbol, period, barCount, null);
    }

    public List<Candle> fetchCandles(String symbol, String period, int barCount,
                                      String currencyCode) throws Exception {
        String normalizedCurrency = null;
        if (currencyCode != null && !currencyCode.isBlank()) {
            normalizedCurrency = currencyCode.toUpperCase();
            if (!VALID_CURRENCIES.contains(normalizedCurrency)) {
                throw new IllegalArgumentException(
                        "Invalid currencyCode: " + currencyCode + " (must be one of " + VALID_CURRENCIES + ")");
            }
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("period", period);
        params.put("barCount", String.valueOf(barCount));
        if (normalizedCurrency != null) {
            params.put("CurrencyCode", normalizedCurrency);
        }
        String url = baseUrl + "/api/chart/v2/" + urlEnc(symbol) + "?" + buildQuery(params);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-API-KEY", apiKey);
        headers.put("Content-Type", "application/json");

        String body = http.get(url, headers);

        JsonElement parsed;
        try {
            parsed = gson.fromJson(body, JsonElement.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Velzon market data: malformed JSON for " + symbol + ": " + truncate(body), e);
        }
        if (parsed == null || !parsed.isJsonArray()) {
            throw new IllegalStateException(
                    "Velzon market data: expected JSON array for " + symbol + ", got: " + truncate(body));
        }

        JsonArray array = parsed.getAsJsonArray();
        List<Candle> candles = new ArrayList<>(array.size());
        for (JsonElement el : array) {
            JsonObject o = el.getAsJsonObject();
            candles.add(new Candle(
                    o.get("date").getAsString(),
                    o.get("open").getAsDouble(),
                    o.get("high").getAsDouble(),
                    o.get("low").getAsDouble(),
                    o.get("close").getAsDouble(),
                    o.get("vol").getAsDouble(),
                    o.get("size").getAsDouble()));
        }
        logger.info("Fetched {} candles for {} ({})", candles.size(), symbol, period);
        return candles;
    }

    private static String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(urlEnc(entry.getKey())).append('=').append(urlEnc(entry.getValue()));
        }
        return sb.toString();
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.substring(0, Math.min(300, s.length()));
    }
}
