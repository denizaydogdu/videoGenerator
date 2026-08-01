package com.videogenerator.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.LlmJson;
import com.videogenerator.model.LocalizedStory;
import com.videogenerator.model.Story;
import com.videogenerator.model.StoryScene;
import com.videogenerator.util.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Localizes a story's narrations and produces per-language metadata.
 * Every target language (including English) goes through the same code
 * path: for English this acts as a metadata-generation pass.
 */
public class TranslationService {
    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);
    private final LlmClient llm;
    private final Gson gson = new Gson();

    public TranslationService(LlmClient llm) {
        this.llm = llm;
    }

    /** F6 — dil başına kazanan başlık kalıpları (büyüme playbook'u §A3). */
    private static final java.util.Map<String, String> TITLE_FORMULAS = java.util.Map.of(
            "tr", """
                - "Bu [vakayı/cinayeti] [N] yıldır kimse çözemedi"
                - "Polis bile açıklayamadı: ..."
                - "Türkiye'nin en gizemli [kayıp/faili meçhul] vakası\"""",
            "en", """
                - "What happened to [name]?" (search-query format)
                - "How Police Caught/Lost [case]"
                - "[N] Years Later, No One Can Explain..."\"""",
            "es", """
                - "¿Qué le pasó a [nombre]?"
                - "Nadie ha podido resolver este caso en [N] años"
                - "La policía nunca pudo explicar..."\"""");

    /** F6 — yasal/şeffaflık dipnotu; açıklamanın sonuna otomatik eklenir. */
    private static final java.util.Map<String, String> DISCLAIMERS = java.util.Map.of(
            "tr", "Bu video kamuya yansımış yargı kararlarına ve basına dayanmaktadır.",
            "en", "Based on public court records and published press reports.",
            "es", "Basado en registros judiciales públicos e informes de prensa.");

    public LocalizedStory localize(Story story, String lang) throws ApiException {
        StringBuilder numbered = new StringBuilder();
        int i = 1;
        for (StoryScene scene : story.getScenes()) {
            numbered.append(i++).append(". ").append(scene.getNarration()).append('\n');
        }

        String system = "You localize short documentary scripts. "
                + "Respond with ONLY valid JSON, no markdown fences.";
        String user = String.format("""
                Story title: %s
                On-screen hook text: %s
                Target language code: %s
                Rewrite the following numbered narration lines in the target language,
                keeping the same order, count and dramatic tone. Keep each line the SAME
                length as the original or SHORTER - never expand (this is spoken audio
                with a strict time budget). Also translate the on-screen hook text
                ("hookText", keep it 4-7 punchy words). Then produce viral
                platform metadata (title, description, 3-5 hashtags) in the SAME language.
                Title MUST follow one of these proven formulas for this language:
                %s
                Narrations:
                %s
                JSON shape:
                {"narrations":["..."],"hookText":"...",
                 "metadata":{"title":"...","description":"...","hashtags":["#..."]}}""",
                story.getTitle(), story.getHookText(), lang,
                TITLE_FORMULAS.getOrDefault(lang, TITLE_FORMULAS.get("en")),
                numbered);

        String raw = LlmJson.strip(llm.complete(system, user));
        LocalizedStory loc;
        try {
            loc = gson.fromJson(raw, LocalizedStory.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException("LLM returned invalid JSON for localization", e);
        }
        if (loc == null || loc.getNarrations() == null || loc.getMetadata() == null
                || loc.getNarrations().size() != story.getScenes().size()) {
            throw new IllegalStateException("Localization mismatch for lang=" + lang
                    + ": expected " + story.getScenes().size() + " narrations, got "
                    + (loc == null || loc.getNarrations() == null ? 0 : loc.getNarrations().size()));
        }
        if (!loc.getMetadata().isValid()) {
            throw new IllegalStateException(
                    "Localization produced invalid metadata for lang=" + lang);
        }
        if (loc.getHookText() == null || loc.getHookText().isBlank()) {
            // Kaynak hikâyedeki hook'a geri düş — overlay boş kalmasın
            loc.setHookText(story.getHookText());
        }
        // F6: şeffaflık/yasal dipnot — her açıklamanın sonuna
        String disclaimer = DISCLAIMERS.getOrDefault(lang, DISCLAIMERS.get("en"));
        loc.getMetadata().setDescription(
                loc.getMetadata().getDescription() + "\n\n" + disclaimer);
        logger.info("Localized story to {}: {}", lang, loc.getMetadata().getTitle());
        return loc;
    }
}
