package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.LlmJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Django'nun servis-seviyeli AI Brifing uç noktasından ({@link VelzonBriefingClient})
 * gelen 6 bölümlü ham metni, üç platform için kısaltılmış/uyarlanmış
 * postlara dönüştürür.
 *
 * KURUMSAL AKIŞ bölümü (kurum isimleri + yüzdeler) dahil, ham metnin
 * TAMAMI LLM'e verilir — kullanıcının 2026-08-23 tarihli açık kararı:
 * bu veri artık sosyal medya postlarında da kurum adı + yüzde olarak
 * aynen paylaşılacak (önceki oturumda bu bölüm bilinçli olarak
 * çıkarılıyordu; karar değişti).
 */
public class VelzonAiBriefingPostGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VelzonAiBriefingPostGenerator.class);
    private static final int MAX_X_LEN = 280;

    private static final String SYSTEM = """
            You adapt a Turkish AI-generated stock analysis (from Velzon, a BIST
            stock-market terminal) into short social media posts for three
            platforms: X (Twitter), Instagram, and Facebook — all in Turkish.

            The source text is already brand-safe (no buy/sell commands, no
            guaranteed predictions), but you must additionally:
            - NEVER use words like "AL"/"SAT"/"GÜÇLÜ AL"/"GÜÇLÜ SAT" as a
              standalone command — if the source ends in a [SKOR: ...] tag,
              translate it into descriptive language instead (e.g. "teknik
              görünüm olumsuz" / "teknik görünüm olumlu" / "teknik görünüm
              nötr"), never repeat the raw tag or an imperative buy/sell word.
            - NEVER fabricate numbers not present in the source text.
            - The source may include a "KURUMSAL AKIŞ" (institutional flow)
              section listing specific institution names with buy/sell
              percentages (e.g. "AKD'de IS %35.8 alıcı, YATIRIM FINANSMAN
              %33.1 satıcı"). Include this verbatim — institution names AND
              percentages, not summarized away or genericized — in
              "instagramCaption"/"facebookCaption" whenever the source has
              it. For "x", include it only if it fits within the 280-char
              budget; if not, prioritize the technical/summary content for
              "x" and let the longer captions carry the institutional detail.
            - Each post must end with a call-to-action driving to Velzon's
              terminal: something like "Daha fazla veri için Velzon'da
              https://www.velzon.tr/terminal/ sayfasını inceleyin." (Turkish,
              can be phrased naturally, but the URL must be included verbatim).
            - "x": max 280 characters TOTAL including the CTA and URL — count
              carefully, this is a hard limit.
            - "instagramCaption"/"facebookCaption": longer form is fine, more
              educational detail from the source, ending with the same CTA.
            - "imagePrompt": ONE shared photorealistic, brand-safe image prompt
              (financial dashboard / candlestick chart imagery) used across all
              three platforms — NO people/faces, NO text overlays, NO specific
              price numbers rendered as text-in-image.

            Respond with ONLY valid JSON, no markdown fences:
            {"x":"...","instagramCaption":"...","facebookCaption":"...",
             "imagePrompt":"..."}""";

    private final LlmClient llm;
    private final Gson gson = new Gson();

    public record AdaptedContent(String xText, String instagramCaption,
                                 String facebookCaption, String imagePrompt) {
    }

    public VelzonAiBriefingPostGenerator(LlmClient llm) {
        this.llm = llm;
    }

    public AdaptedContent adapt(VelzonBriefingClient.Briefing briefing) throws Exception {
        String user = String.format("""
                Sembol: %s (zaman dilimi: %s)

                --- ANALİZ METNİ BAŞLIYOR ---
                %s
                --- ANALİZ METNİ BİTTİ ---
                """, briefing.symbol(), briefing.timeframe(), briefing.text());

        String raw = LlmJson.strip(llm.complete(SYSTEM, user));
        JsonObject resp;
        try {
            resp = gson.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM returned invalid JSON for briefing adaptation", e);
        }
        if (resp == null) {
            throw new IllegalStateException("LLM returned empty response for briefing adaptation");
        }

        String xText = requireField(resp, "x");
        if (xText.length() > MAX_X_LEN) {
            throw new IllegalStateException(
                    "X text exceeds " + MAX_X_LEN + " chars (" + xText.length() + "): " + xText);
        }

        AdaptedContent result = new AdaptedContent(
                xText,
                requireField(resp, "instagramCaption"),
                requireField(resp, "facebookCaption"),
                requireField(resp, "imagePrompt"));
        logger.info("Adapted AI briefing for {} into 3-platform content", briefing.symbol());
        return result;
    }

    private static String requireField(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) {
            throw new IllegalStateException("LLM response missing required field '" + field + "'");
        }
        return o.get(field).getAsString();
    }
}
