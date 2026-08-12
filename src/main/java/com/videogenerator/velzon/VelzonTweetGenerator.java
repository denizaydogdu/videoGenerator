package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.LlmJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Velzon (BIST — Borsa İstanbul — analiz terminali: endeksler, teknik/temel
 * göstergeler, AI destekli hisse skorlama, portföy araçları, Pine Script
 * tabanlı özel gösterge eğitimi; TradingView/Fintables benzeri bir ürün)
 * için X (Twitter) tweet taslakları üretir — Pinterest deseninin metin-only
 * versiyonu (görsel yok).
 *
 * Girdi artık serbest bir "niche" ifadesi DEĞİL: Velzon'un kendi bilgi
 * merkezinden (VelzonKnowledgeBaseClient) çekilmiş GERÇEK bir makalenin tam
 * metni (başlık + gövde) — bkz. generateBatch javadoc'u.
 *
 * Otomatik gönderim YOK: taslaklar manifest.json'a yazılır, backoffice'te
 * insan onayından (Yayınla tıklaması) geçmeden gerçek hesaba gitmez —
 * bir yatırım/borsa platformu hesabının yanlış/yanıltıcı bilgi paylaşma
 * riski, maliyetten (X API ucuz) çok daha önemli bir güvenlik kapısı.
 */
public class VelzonTweetGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VelzonTweetGenerator.class);
    private static final int MAX_TWEET_LEN = 280;
    private static final String SYSTEM = """
            You write X (Twitter) posts for Velzon, a Turkish BIST (Borsa İstanbul)
            stock-market analysis terminal, in Turkish. Velzon provides real-time
            index tracking, AI-assisted stock scoring, technical/fundamental
            indicators, portfolio tools, and Pine-Script-based custom indicator
            education — think TradingView/Fintables, but focused on the Turkish
            market.

            Your input is the full text of ONE REAL Velzon knowledge-base article
            (its title, then its body). Your job is to summarize and adapt THAT
            article's actual content into tweet ideas — do NOT invent a new topic,
            and do NOT add facts, figures, or claims that are not present in the
            article text. Stay faithful to what the article actually says.

            HARD RULES (violations are unacceptable for a stock-market platform's account):
            - NEVER recommend buying or selling any specific stock, or imply a
              stock is a good/bad investment right now.
            - NEVER claim or imply guaranteed returns, guaranteed accuracy, or
              guaranteed prediction outcomes.
            - NEVER fabricate performance statistics, win rates, or numbers that
              are not explicitly present in the source article.
            - NEVER give this the tone of personalized investment advice — keep it
              educational/informational (what a term means, how an indicator
              works, how a platform feature works), never "you should do X with
              your money."
            - NEVER mention competitor company names.
            - Each tweet must be 280 characters or fewer (hard platform limit) —
              count carefully, this is not negotiable.

            Respond with ONLY valid JSON, no markdown fences.""";

    private final LlmClient llm;
    private final Gson gson = new Gson();

    public record Tweet(String topic, String text) {
    }

    public VelzonTweetGenerator(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * @param topicPrompt the full text of ONE real Velzon knowledge-base
     *                     article (title + body, concatenated) — NOT a free
     *                     invented topic. Callers should fetch this via
     *                     VelzonKnowledgeBaseClient.fetchArticle(path) first.
     */
    public List<Tweet> generateBatch(String topicPrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Below is the full text of a real Velzon knowledge-base article
                (title, then body). Generate %d tweet ideas that summarize or
                adapt THIS article's actual content for X (Twitter) — different
                angles on the same article, not unrelated topics.

                --- ARTICLE START ---
                %s
                --- ARTICLE END ---

                Each idea needs:
                - "topic": short internal label for this tweet's angle on the article
                - "text": the tweet body in Turkish, 280 chars or fewer

                JSON shape: {"tweets":[{"topic":"...","text":"..."}]}
                """, count, topicPrompt);

        String raw = LlmJson.strip(llm.complete(SYSTEM, user));
        JsonObject parsed;
        try {
            parsed = gson.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM returned invalid JSON for tweet batch", e);
        }
        if (parsed == null || !parsed.has("tweets")) {
            throw new IllegalStateException("LLM response missing 'tweets' array: " + raw);
        }
        JsonArray tweetsJson = parsed.getAsJsonArray("tweets");
        if (tweetsJson.size() != count) {
            throw new IllegalStateException("LLM returned " + tweetsJson.size()
                    + " tweets, expected " + count);
        }

        Files.createDirectories(outDir);
        List<Tweet> tweets = new ArrayList<>();
        JsonArray manifest = new JsonArray();

        for (int i = 0; i < tweetsJson.size(); i++) {
            JsonObject t = tweetsJson.get(i).getAsJsonObject();
            String topic = requireField(t, "topic", i);
            String text = requireField(t, "text", i);
            if (text.length() > MAX_TWEET_LEN) {
                throw new IllegalStateException("Tweet " + (i + 1) + " exceeds "
                        + MAX_TWEET_LEN + " chars (" + text.length() + "): " + text);
            }

            tweets.add(new Tweet(topic, text));

            JsonObject entry = new JsonObject();
            entry.addProperty("topic", topic);
            entry.addProperty("text", text);
            entry.addProperty("published", false);
            manifest.add(entry);
        }

        Files.writeString(outDir.resolve("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        logger.info("Velzon tweet batch written: {}", outDir.toAbsolutePath());
        return tweets;
    }

    private static String requireField(JsonObject o, String field, int index) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) {
            throw new IllegalStateException(
                    "Tweet " + (index + 1) + " missing required field '" + field + "'");
        }
        return o.get(field).getAsString();
    }
}
