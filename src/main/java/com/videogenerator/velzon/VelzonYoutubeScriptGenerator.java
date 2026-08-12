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
 * Velzon (fintech/e-fatura şirketi) için YouTube Shorts video "senaryoları"
 * üretir — VelzonInstagramPostGenerator'ın güvenlik kurallarını, ~30-45
 * saniyelik sesli anlatım (narration) + tek arkaplan görseli formatına
 * uyarlar.
 *
 * KASITLI OLARAK ImageGenerator BAĞIMLILIĞI YOK: Instagram/Pinterest
 * üreteçlerinin aksine bu sınıf, parti üretimi sırasında görsel/seslendirme/
 * render YAPMAZ — bunlar pahalı ve yavaş adımlar (TTS + FFmpeg render), bir
 * partideki N senaryonun hepsini insan hiçbirini seçmeden önce render etmek
 * israf olur. Görsel+seslendirme+video üretimi VelzonYoutubeVideoBuilder
 * tarafından TEMBEL biçimde, yalnızca "Yayınla" tıklandığında yapılır (bkz.
 * VelzonYoutubePublishService). Bu sınıf yalnızca metin (LLM) üretir ve
 * planlı görsel dosya adını (henüz var olmayan) manifest.json'a yazar.
 *
 * Otomatik yayın YOK: taslaklar manifest.json'a yazılır, backoffice'te
 * insan onayından (Yayınla tıklaması) geçmeden gerçek kanala gitmez.
 */
public class VelzonYoutubeScriptGenerator {
    private static final Logger logger = LoggerFactory.getLogger(VelzonYoutubeScriptGenerator.class);
    private static final String SYSTEM = """
            You write short spoken video scripts for Velzon, a Turkish fintech / e-invoicing
            (e-fatura) company's YouTube Shorts channel, in Turkish. Educational, general-practice
            tone about e-fatura, muhasebe otomasyonu, and KOBİ finans yönetimi.

            HARD RULES (violations are unacceptable for a financial company's account):
            - NEVER state specific tax rates, KDV percentages, or legal/tax figures —
              they change over time and could mislead readers.
            - NEVER give definitive legal or tax advice — use general, educational
              phrasing, not prescriptive claims.
            - NEVER mention competitor company names.
            - NEVER fabricate customer testimonials, statistics, or specific numbers.

            Each script needs:
            - "narration": spoken narration text in Turkish, meant to be read aloud in
              roughly 30-45 seconds at a natural pace — that is approximately 75-115
              words. Do not write something wildly shorter or longer than that.
            - "title": a compelling YouTube title in Turkish, under 100 characters
            - "description": a short YouTube description in Turkish
            - "hashtags": 3-6 relevant Turkish fintech/e-fatura hashtags
              (e.g. #efatura #muhasebe #kobi #fintech)
            - "imagePrompt": a photorealistic, brand-safe image prompt describing a
              SINGLE calm background scene (office/finance/desk/dashboard imagery),
              NO people/faces, NO text overlays, vertical 9:16 framing — it becomes
              a single still image that is slowly zoomed/panned (Ken Burns effect)
              behind the whole video, so avoid busy multi-subject scenes.

            Respond with ONLY valid JSON, no markdown fences.""";

    private final LlmClient llm;
    private final Gson gson = new Gson();

    public record Script(String narration, String title, String description,
                         List<String> hashtags, String imagePrompt, String imageFile) {
    }

    public VelzonYoutubeScriptGenerator(LlmClient llm) {
        this.llm = llm;
    }

    public List<Script> generateBatch(String topicPrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Generate %d YouTube Shorts video script ideas about: %s.

                JSON shape: {"scripts":[{"narration":"...","title":"...",
                "description":"...","hashtags":["...","..."],"imagePrompt":"..."}]}
                """, count, topicPrompt);

        String raw = LlmJson.strip(llm.complete(SYSTEM, user));
        JsonObject parsed;
        try {
            parsed = gson.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM returned invalid JSON for YouTube script batch", e);
        }
        if (parsed == null || !parsed.has("scripts")) {
            throw new IllegalStateException("LLM response missing 'scripts' array: " + raw);
        }
        JsonArray scriptsJson = parsed.getAsJsonArray("scripts");
        if (scriptsJson.size() != count) {
            throw new IllegalStateException("LLM returned " + scriptsJson.size()
                    + " scripts, expected " + count);
        }

        Files.createDirectories(outDir);
        List<Script> scripts = new ArrayList<>();
        JsonArray manifest = new JsonArray();

        for (int i = 0; i < scriptsJson.size(); i++) {
            JsonObject s = scriptsJson.get(i).getAsJsonObject();
            String narration = requireField(s, "narration", i);
            String title = requireField(s, "title", i);
            String description = requireField(s, "description", i);
            String imagePrompt = requireField(s, "imagePrompt", i);
            List<String> hashtags = requireHashtags(s, i);

            String imageFile = String.format("video-%02d.png", i + 1);
            Script script = new Script(narration, title, description, hashtags,
                    imagePrompt, imageFile);
            scripts.add(script);

            JsonObject entry = new JsonObject();
            entry.addProperty("narration", narration);
            entry.addProperty("title", title);
            entry.addProperty("description", description);
            JsonArray tagsJson = new JsonArray();
            hashtags.forEach(tagsJson::add);
            entry.add("hashtags", tagsJson);
            entry.addProperty("imagePrompt", imagePrompt);
            entry.addProperty("imageFile", imageFile);
            entry.addProperty("published", false);
            manifest.add(entry);
        }

        Files.writeString(outDir.resolve("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        logger.info("Velzon YouTube script batch written: {}", outDir.toAbsolutePath());
        return scripts;
    }

    private static String requireField(JsonObject o, String field, int index) {
        if (!o.has(field) || o.get(field).getAsString().isBlank()) {
            throw new IllegalStateException(
                    "Script " + (index + 1) + " missing required field '" + field + "'");
        }
        return o.get(field).getAsString();
    }

    private static List<String> requireHashtags(JsonObject o, int index) {
        if (!o.has("hashtags") || !o.get("hashtags").isJsonArray()
                || o.getAsJsonArray("hashtags").isEmpty()) {
            throw new IllegalStateException(
                    "Script " + (index + 1) + " missing required field 'hashtags'");
        }
        List<String> tags = new ArrayList<>();
        for (var el : o.getAsJsonArray("hashtags")) {
            tags.add(el.getAsString());
        }
        return tags;
    }
}
