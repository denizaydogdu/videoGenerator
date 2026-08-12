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
 * Velzon (fintech/e-fatura şirketi) için Instagram gönderi taslakları
 * üretir — Pinterest'in görsel+metin şeklini, VelzonTweetGenerator'ın
 * güvenlik kurallarıyla birleştirir.
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
            You write Instagram posts for Velzon, a Turkish fintech / e-invoicing
            (e-fatura) company, in Turkish. Educational, general-practice tone about
            e-fatura, muhasebe otomasyonu, and KOBİ finans yönetimi.

            HARD RULES (violations are unacceptable for a financial company's account):
            - NEVER state specific tax rates, KDV percentages, or legal/tax figures —
              they change over time and could mislead readers.
            - NEVER give definitive legal or tax advice — use general, educational
              phrasing, not prescriptive claims.
            - NEVER mention competitor company names.
            - NEVER fabricate customer testimonials, statistics, or specific numbers.
            - Each caption should end with 3-8 relevant Turkish fintech/e-fatura
              hashtags (e.g. #efatura #muhasebe #kobi #fintech) — do not invent
              engagement-bait hashtags unrelated to the topic.

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

    public List<Post> generateBatch(String topicPrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Generate %d Instagram post ideas about: %s.

                Each idea needs:
                - "caption": the Instagram caption in Turkish, educational and
                  non-promotional in tone, ending with 3-8 relevant hashtags
                - "imagePrompt": a photorealistic, brand-safe image prompt
                  (office/finance/desk/dashboard imagery), NO people/faces, NO text
                  overlays, square 1:1 framing

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
