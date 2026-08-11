package com.videogenerator.pinterest;

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
 * Pinterest pin fikirleri (başlık + görsel + açıklama + alt text) üretir.
 * Manifest her pin için published/url alanları taşır — backoffice'in
 * yayın durumunu takip etmesi için (bkz. PinterestPublishService).
 *
 * Çıktıya gerçek ürün linki EKLEMEZ — affiliate onayı (Amazon Associates)
 * gelene ve kullanıcı gerçek ürünleri SiteStripe ile seçene kadar
 * uydurma/tahmini URL üretmek yanlış olur.
 */
public class PinterestBatchGenerator {
    private static final Logger logger = LoggerFactory.getLogger(PinterestBatchGenerator.class);
    private static final String SYSTEM = "You create Pinterest content for a home decor "
            + "affiliate account. Respond with ONLY valid JSON, no markdown fences.";

    private final LlmClient llm;
    private final ImageGenerator imageGen;
    private final Gson gson = new Gson();

    public record Pin(String title, String imagePrompt, String description,
                      String altText, String file) {
    }

    public PinterestBatchGenerator(LlmClient llm, ImageGenerator imageGen) {
        this.llm = llm;
        this.imageGen = imageGen;
    }

    public List<Pin> generateBatch(String nichePrompt, int count, Path outDir) throws Exception {
        String user = String.format("""
                Generate %d Pinterest pin ideas for: %s.
                High purchase-intent audience actively searching to buy. Mix formats:
                before/after room makeovers, "best N products for X" lists,
                "N items to upgrade Y" lists. Each idea needs:
                - "title": punchy Pinterest-style title (under 100 chars)
                - "imagePrompt": a photorealistic staged-room or product-flatlay image
                  prompt, bright natural light, aspirational aesthetic, NO people/faces,
                  2:3 portrait framing
                - "description": Pinterest description (150-300 chars) with a natural
                  product recommendation angle and 3-5 hashtags. Do NOT invent fake
                  prices, fake brand names, or URLs.
                - "altText": short objective description of the image for screen
                  readers (under 150 chars, no hashtags, describe what's visible)

                JSON shape: {"pins":[{"title":"...","imagePrompt":"...",
                "description":"...","altText":"..."}]}
                """, count, nichePrompt);

        String raw = LlmJson.strip(llm.complete(SYSTEM, user));
        JsonObject parsed;
        try {
            parsed = gson.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("LLM returned invalid JSON for pin batch", e);
        }
        if (parsed == null || !parsed.has("pins")) {
            throw new IllegalStateException("LLM response missing 'pins' array: " + raw);
        }
        JsonArray pinsJson = parsed.getAsJsonArray("pins");
        if (pinsJson.size() != count) {
            throw new IllegalStateException("LLM returned " + pinsJson.size()
                    + " pins, expected " + count);
        }

        Files.createDirectories(outDir);
        List<Pin> pins = new ArrayList<>();
        JsonArray manifest = new JsonArray();

        for (int i = 0; i < pinsJson.size(); i++) {
            JsonObject p = pinsJson.get(i).getAsJsonObject();
            String title = requireField(p, "title", i);
            String imagePrompt = requireField(p, "imagePrompt", i);
            String description = requireField(p, "description", i);
            String altText = requireField(p, "altText", i);

            String fileName = String.format("pin-%02d.png", i + 1);
            Path imgPath = outDir.resolve(fileName);
            logger.info("{}/{} generating: {}", i + 1, count, title);
            imageGen.generate(imagePrompt, imgPath);

            pins.add(new Pin(title, imagePrompt, description, altText, fileName));

            JsonObject entry = new JsonObject();
            entry.addProperty("file", fileName);
            entry.addProperty("title", title);
            entry.addProperty("description", description);
            entry.addProperty("altText", altText);
            entry.addProperty("published", false);
            manifest.add(entry);
        }

        Files.writeString(outDir.resolve("manifest.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        logger.info("Pinterest batch written: {}", outDir.toAbsolutePath());
        return pins;
    }

    private static String requireField(JsonObject pin, String field, int index) {
        if (!pin.has(field) || pin.get(field).getAsString().isBlank()) {
            throw new IllegalStateException(
                    "Pin " + (index + 1) + " missing required field '" + field + "'");
        }
        return pin.get(field).getAsString();
    }
}
