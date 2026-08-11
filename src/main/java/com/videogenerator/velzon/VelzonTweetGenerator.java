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
 * Velzon (fintech/e-fatura şirketi) için X (Twitter) tweet taslakları
 * üretir — Pinterest deseninin metin-only versiyonu (görsel yok).
 *
 * Otomatik gönderim YOK: taslaklar manifest.json'a yazılır, backoffice'te
 * insan onayından (Yayınla tıklaması) geçmeden gerçek hesaba gitmez —
 * finans şirketi hesabının yanlış/yanıltıcı bilgi paylaşma riski,
 * maliyetten (X API ucuz) çok daha önemli bir güvenlik kapısı.
 */
public class VelzonTweetGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VelzonTweetGenerator.class);
    private static final int MAX_TWEET_LEN = 280;
    private static final String SYSTEM = """
            You write X (Twitter) posts for Velzon, a Turkish fintech / e-invoicing
            (e-fatura) company, in Turkish. Educational, general-practice tone about
            e-fatura, muhasebe otomasyonu, and KOBİ finans yönetimi.

            HARD RULES (violations are unacceptable for a financial company's account):
            - NEVER state specific tax rates, KDV percentages, or legal/tax figures —
              they change over time and could mislead readers.
            - NEVER give definitive legal or tax advice — use general, educational
              phrasing, not prescriptive claims.
            - NEVER mention competitor company names.
            - NEVER fabricate customer testimonials, statistics, or specific numbers.
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

    public List<Tweet> generateBatch(String topicPrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Generate %d tweet ideas about: %s.

                Each idea needs:
                - "topic": short internal label for this tweet's theme
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
