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
              percentages and lot figures (e.g. "AKD'de IS %35.8 alıcı
              (+38.762 lot), YATIRIM FINANSMAN %33.1 satıcı"). Include this
              verbatim — institution names AND percentages/lot numbers, not
              summarized away or genericized — in "instagramCaption"/
              "facebookCaption" whenever the source has it, regardless of
              the "x" focus below.
            - For "x": the user message tells you, PER REQUEST, which content
              to lead with — either TEKNİK GÖRÜNÜM or KURUMSAL AKIŞ. Write
              that one first; if the 280-char budget leaves room, briefly
              mention the other one second. Never skip the requested focus
              to make room for the other — the requested focus always wins
              the limited space.
            - EVERY post ("x", "instagramCaption", "facebookCaption") MUST
              explicitly state the stock symbol (given in the user message
              as "Sembol: X") — a reader must know which stock this is
              about at a glance. NEVER omit it to save space, regardless of
              which content leads "x". Natural Turkish suffix forms are fine
              ("THYAO'nun", "THYAO'da").
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
    private final java.util.function.Supplier<Boolean> xFocusChooser;

    public record AdaptedContent(String xText, String instagramCaption,
                                 String facebookCaption, String imagePrompt) {
    }

    public VelzonAiBriefingPostGenerator(LlmClient llm) {
        this(llm, alternatingXFocusChooser());
    }

    /**
     * Bağımsız %50 yazı-tura DEĞİL — rastgele bir başlangıçtan sonra KESİN
     * OLARAK alternate eder (arka arkaya iki kez aynı seçim asla çıkmaz).
     * Kullanıcının 2026-08-24 endişesi haklıydı: bağımsız rastgelelikte
     * günde 5 turun hepsinin şansla "teknik" çıkması istatistiksel olarak
     * mümkündür (%3+ ihtimal 5 turda) — bu, o riski sıfırlar.
     */
    private static java.util.function.Supplier<Boolean> alternatingXFocusChooser() {
        var state = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        return () -> state.updateAndGet(prev -> prev == null
                ? java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                : !prev);
    }

    /**
     * Testte "x" odağını (teknik/kurumsal akış) deterministik seçmek için —
     * kullanıcının 2026-08-24 talebi: X tweet'i her zaman teknik özete
     * öncelik vermesin, bazen KURUMSAL AKIŞ verisi de öne çıksın. Gerçek
     * seçim {@code xFocusChooser}'a bırakılır (varsayılan: {@link
     * #alternatingXFocusChooser}); KURUMSAL AKIŞ bölümünde veri yoksa
     * ({@link #hasInstitutionalFlowData}) seçim ne olursa olsun teknik
     * odağa düşülür — olmayan veriye öncelik vermenin anlamı yok (bu
     * durumda alternatör state'i yine de ilerler, bir sonraki gerçek
     * fırsatta diğer taraf denenmiş olur).
     */
    VelzonAiBriefingPostGenerator(LlmClient llm, java.util.function.Supplier<Boolean> xFocusChooser) {
        this.llm = llm;
        this.xFocusChooser = xFocusChooser;
    }

    /** KURUMSAL AKIŞ bölümünde gerçek veri var mı — "bağlamda yok" tek cümlesi değil mi. */
    private static boolean hasInstitutionalFlowData(String text) {
        int idx = text.indexOf("KURUMSAL AKIŞ");
        if (idx < 0) {
            return false;
        }
        int end = text.indexOf("RİSK", idx);
        String section = end > idx ? text.substring(idx, end) : text.substring(idx);
        return !section.contains("bağlamda yok");
    }

    public AdaptedContent adapt(VelzonBriefingClient.Briefing briefing) throws Exception {
        boolean prioritizeInstitutionalForX = hasInstitutionalFlowData(briefing.text())
                && xFocusChooser.get();
        String xFocusNote = prioritizeInstitutionalForX
                ? "Bu tur için \"x\" alanında ÖNCELİK KURUMSAL AKIŞ verisinde "
                        + "(kurum adı + lot/yüzde) olsun — önce o cümleyi yaz, yer "
                        + "kalırsa teknik özeti kısaca ekle."
                : "Bu tur için \"x\" alanında ÖNCELİK TEKNİK GÖRÜNÜM özetinde olsun "
                        + "— önce o cümleyi yaz, yer kalırsa kurumsal akıştan kısaca bahset.";

        String user = String.format("""
                Sembol: %s (zaman dilimi: %s)

                %s

                --- ANALİZ METNİ BAŞLIYOR ---
                %s
                --- ANALİZ METNİ BİTTİ ---
                """, briefing.symbol(), briefing.timeframe(), xFocusNote, briefing.text());

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
        String instagramCaption = requireField(resp, "instagramCaption");
        String facebookCaption = requireField(resp, "facebookCaption");

        // Prompt talimatı yeterli olmayabilir (bkz. 2026-08-24: KURUMSAL AKIŞ
        // odağı istenince LLM sembolü hiç yazmadan attı) — sessizce
        // yayınlamak yerine o turu atlamak daha güvenli.
        requireSymbolMentioned(xText, "x", briefing.symbol());
        requireSymbolMentioned(instagramCaption, "instagramCaption", briefing.symbol());
        requireSymbolMentioned(facebookCaption, "facebookCaption", briefing.symbol());

        AdaptedContent result = new AdaptedContent(
                xText, instagramCaption, facebookCaption,
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

    /** Okuyucu hangi hisseden bahsedildiğini bilmeli — sessizce eksik geçilmez. */
    private static void requireSymbolMentioned(String text, String field, String symbol) {
        if (!text.toUpperCase(java.util.Locale.ROOT).contains(symbol.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException(
                    "LLM response field '" + field + "' does not mention symbol '" + symbol + "': " + text);
        }
    }
}
