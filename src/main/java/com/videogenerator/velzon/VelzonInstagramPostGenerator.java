package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.videogenerator.api.ImageGenerator;
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
 * tabanlı özel gösterge eğitimi) için Instagram gönderi taslakları üretir —
 * Pinterest'in görsel+metin şeklini, VelzonTweetGenerator'ın güvenlik
 * kurallarıyla birleştirir.
 *
 * Girdi artık serbest bir "niche" ifadesi DEĞİL: Velzon'un kendi bilgi
 * merkezinden (VelzonKnowledgeBaseClient) çekilmiş GERÇEK bir makalenin tam
 * metni (başlık + gövde) — bkz. generateBatch javadoc'u.
 *
 * Otomatik gönderim YOK: taslaklar manifest.json'a yazılır, backoffice'te
 * insan onayından (Yayınla tıklaması) geçmeden gerçek hesaba gitmez —
 * VelzonTweetGenerator'daki gerekçenin aynısı geçerli.
 *
 * Instagram Content Publishing API oluşturma isteğinde alt text alanı
 * DESTEKLEMEZ (yalnızca Graph video/albüm akışlarında farklı bir alan var,
 * tekil resim container'ında yok) — bu yüzden Pinterest'teki altText
 * alanına burada kasıtlı olarak yer verilmedi.
 */
public class VelzonInstagramPostGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VelzonInstagramPostGenerator.class);
    private static final String SYSTEM = """
            You write Instagram posts for Velzon, a Turkish BIST (Borsa İstanbul)
            stock-market analysis terminal, in Turkish. Velzon provides real-time
            index tracking, AI-assisted stock scoring, technical/fundamental
            indicators, portfolio tools, and Pine-Script-based custom indicator
            education — think TradingView/Fintables, but focused on the Turkish
            market.

            Your input is the full text of ONE REAL Velzon knowledge-base article
            (its title, then its body). Your job is to summarize and adapt THAT
            article's actual content into Instagram post ideas — do NOT invent a
            new topic, and do NOT add facts, figures, or claims that are not
            present in the article text. Stay faithful to what the article
            actually says.

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
            - Each caption should end with 3-8 relevant Turkish BIST/trading
              hashtags (e.g. #borsa #bist100 #yatirim #hisse #teknikanaliz) — do
              not invent engagement-bait hashtags unrelated to the topic.

            Respond with ONLY valid JSON, no markdown fences.""";

    private final LlmClient llm;
    private final ImageGenerator imageGen;
    private final Gson gson = new Gson();

    public record Post(String caption, String imagePrompt, String file) {
    }

    public VelzonInstagramPostGenerator(LlmClient llm, ImageGenerator imageGen) {
        this.llm = llm;
        this.imageGen = imageGen;
    }

    /**
     * @param topicPrompt the full text of ONE real Velzon knowledge-base
     *                     article (title + body, concatenated) — NOT a free
     *                     invented topic. Callers should fetch this via
     *                     VelzonKnowledgeBaseClient.fetchArticle(path) first.
     */
    public List<Post> generateBatch(String topicPrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Below is the full text of a real Velzon knowledge-base article
                (title, then body). Generate %d Instagram post ideas that
                summarize or adapt THIS article's actual content — different
                angles on the same article, not unrelated topics.

                --- ARTICLE START ---
                %s
                --- ARTICLE END ---

                Each idea needs:
                - "caption": the Instagram caption in Turkish, educational and
                  non-promotional in tone, ending with 3-8 relevant hashtags
                - "imagePrompt": a photorealistic, brand-safe image prompt
                  (stock market / trading terminal / candlestick chart / financial
                  dashboard imagery), NO people/faces, NO text overlays, square
                  1:1 framing

                JSON shape: {"posts":[{"caption":"...","imagePrompt":"..."}]}
                """, count, topicPrompt);

        String raw = LlmJson.strip(llm.complete(SYSTEM, user));
        JsonObject parsed;
        try {
            parsed = gson.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM returned invalid JSON for Instagram post batch", e);
        }
        if (parsed == null || !parsed.has("posts")) {
            throw new IllegalStateException("LLM response missing 'posts' array: " + raw);
        }
        JsonArray postsJson = parsed.getAsJsonArray("posts");
        if (postsJson.size() != count) {
            throw new IllegalStateException("LLM returned " + postsJson.size()
                    + " posts, expected " + count);
        }

        Files.createDirectories(outDir);
        List<Post> posts = new ArrayList<>();
        JsonArray manifest = new JsonArray();

        for (int i = 0; i < postsJson.size(); i++) {
            JsonObject p = postsJson.get(i).getAsJsonObject();
            String caption = requireField(p, "caption", i);
            String imagePrompt = requireField(p, "imagePrompt", i);

            String fileName = String.format("post-%02d.png", i + 1);
            Path imgPath = outDir.resolve(fileName);
            logger.info("{}/{} generating Instagram post image", i + 1, count);
            imageGen.generate(imagePrompt, imgPath);

            posts.add(new Post(caption, imagePrompt, fileName));

            JsonObject entry = new JsonObject();
            entry.addProperty("file", fileName);
            entry.addProperty("caption", caption);
            entry.addProperty("published", false);
            manifest.add(entry);
        }

        Files.writeString(outDir.resolve("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        logger.info("Velzon Instagram batch written: {}", outDir.toAbsolutePath());
        return posts;
    }

    private static String requireField(JsonObject o, String field, int index) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) {
            throw new IllegalStateException(
                    "Post " + (index + 1) + " missing required field '" + field + "'");
        }
        return o.get(field).getAsString();
    }
}
