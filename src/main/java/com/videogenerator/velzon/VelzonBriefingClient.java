package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * velzon-django'nun yeni, servis-seviyeli AI Brifing uç noktasına
 * ({@code GET /api/ai/symbol-briefing/service/}) X-API-KEY ile bağlanır.
 * Bu uç, kullanıcı-giriş gerektiren mevcut SSE tabanlı AI Brifing
 * özelliğinin (velzon.tr/terminal, "Yapay Zeka" butonu) senkron/JSON
 * varyantıdır — kişisel kullanıcı bağlamı taşımaz, tek seferde tam metni
 * döner (akış yok). {@link VelzonMarketDataClient}'ın (gate.velzon.tr)
 * aynı X-API-KEY desenini izler.
 */
public class VelzonBriefingClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonBriefingClient.class);

    public interface Http {
        String get(String url, Map<String, String> headers) throws Exception;
    }

    public record Briefing(String symbol, String timeframe, String text) {
    }

    private final Http http;
    private final String baseUrl;
    private final String apiKey;
    private final Gson gson = new Gson();

    public VelzonBriefingClient(Http http, String baseUrl, String apiKey) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public Briefing fetchBriefing(String symbol, String timeframe) throws Exception {
        String url = baseUrl + "/api/ai/symbol-briefing/service/?symbol=" + urlEnc(symbol)
                + "&tf=" + urlEnc(timeframe);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-API-KEY", apiKey);

        String body = http.get(url, headers);

        JsonObject resp;
        try {
            resp = gson.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Velzon briefing: malformed JSON for " + symbol + ": " + truncate(body), e);
        }
        if (resp == null || !resp.has("text")) {
            throw new IllegalStateException(
                    "Velzon briefing: response missing 'text' for " + symbol + ": " + truncate(body));
        }

        String respSymbol = resp.has("symbol") ? resp.get("symbol").getAsString() : symbol;
        String respTf = resp.has("timeframe") ? resp.get("timeframe").getAsString() : timeframe;
        String text = resp.get("text").getAsString();
        logger.info("Fetched AI briefing for {} ({}): {} chars", respSymbol, respTf, text.length());
        return new Briefing(respSymbol, respTf, text);
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.substring(0, Math.min(300, s.length()));
    }
}
