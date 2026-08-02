package com.videogenerator.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.LlmJson;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.Story;
import com.videogenerator.util.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a complete story (narration + image prompt per scene) in a
 * SINGLE LLM call so visuals always match what is being narrated.
 */
public class StoryWriter {
    private static final Logger logger = LoggerFactory.getLogger(StoryWriter.class);
    private final LlmClient llm;
    private final Gson gson = new Gson();

    public StoryWriter(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * Anti-şablon tempo varyantları: YouTube'un 2026 "inauthentic content"
     * uygulaması kanal içi tekrar eden yapı kalıplarını kümeleyerek tespit
     * ediyor — her video farklı bir anlatı ritmi talimatı alır.
     */
    private static final String[] PACING_VARIANTS = {
            "short punchy sentences, rapid factual beats",
            "one slow-burn revelation per scene, measured tone",
            "question-driven: each scene answers the previous scene's question",
            "evidence-first: lead each scene with a concrete piece of evidence",
            "timeline-jumps: alternate between the event and the investigation"
    };

    public Story write(ContentIdea idea, ChannelProfile profile) throws ApiException {
        String system = "You write scripts for short vertical documentary videos "
                + "about REAL criminal cases and mysteries. "
                + "Respond with ONLY valid JSON, no markdown fences.";
        // 2.2 kelime/sn: TTS temposu + ES/TR çevirilerinin ~%15 uzaması payı.
        int totalWords = (int) Math.round(profile.getTargetDurationSeconds() * 2.2);
        int wordsPerScene = totalWords / profile.getSceneCount();
        String pacing = PACING_VARIANTS[
                new java.util.Random().nextInt(PACING_VARIANTS.length)];
        String user = String.format("""
                Topic: %s
                Niche: %s

                Write a gripping %d-second story in English split into EXACTLY %d scenes.

                FACTUAL RULES (violations are channel-terminating):
                - The case must be REAL and verifiable: either a court verdict is
                  finalized, or it has been an officially unsolved cold case for 10+ years.
                - Never invent cases, people, evidence or "secret" facts. If unsure, pick
                  a better-documented case.
                - Use "alleged"/"reportedly" language for anything not established by a
                  final verdict. Never assert guilt of a named living person.
                - Include at least ONE primary-source detail (court record, police report
                  or contemporary press fact).

                HOOK RULES:
                - Scene 1 narration states the most shocking OUTCOME first.
                - Never open with a date or scene-setting ("In October 1975..." is banned).
                - Also produce "hookText": a 4-7 word on-screen text hook.

                LOOP RULE:
                - The final line must recontextualize the opening line so the video loops
                  seamlessly into a rewatch.

                Pacing style for THIS video: %s

                HARD LIMIT: total narration must not exceed %d words (about %d words per scene).
                Each scene: 1-2 spoken sentences ("narration") and TWO visual descriptions
                ("imagePrompts", different angles/subjects of the same beat) showing PLACES,
                OBJECTS, DOCUMENTS or SILHOUETTES - never a recognizable human face and
                never a real person's likeness.
                End the last scene on a specific unresolved question that makes viewers
                want to share and comment a full-sentence theory.

                JSON shape:
                {"title": "...", "hookText": "...",
                 "scenes":[{"narration":"...","imagePrompts":["...","..."]}]}""",
                idea.getTitle(), profile.getNiche().getTopic(),
                profile.getTargetDurationSeconds(), profile.getSceneCount(),
                pacing, totalWords, wordsPerScene);

        // GPT ara sıra sahne dizisini bozuyor (canlı: `},{` ayraçları atlanıp
        // tüm sahneler tek objeye yazıldı -> 1 sahne). Doğrulama hatası
        // deterministik değil; taze bir denemede genelde düzeliyor.
        final int maxAttempts = 3;
        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String prompt = attempt == 1 ? user : user + String.format("""

                    STRICT FORMAT REMINDER (a previous attempt was rejected: %s):
                    "scenes" must be a JSON ARRAY of EXACTLY %d separate objects,
                    each object with its own "narration" and "imagePrompts" keys.""",
                    lastFailure.getMessage(), profile.getSceneCount());
            try {
                Story story = attemptStory(system, prompt, profile);
                logger.info("Story written: {} ({} scenes)",
                        story.getTitle(), story.getScenes().size());
                return story;
            } catch (IllegalStateException e) {
                lastFailure = e;
                logger.warn("Story attempt {}/{} rejected: {}",
                        attempt, maxAttempts, e.getMessage());
            }
        }
        throw lastFailure;
    }

    private Story attemptStory(String system, String user, ChannelProfile profile)
            throws ApiException {
        String raw = LlmJson.strip(llm.complete(system, user));
        Story story;
        try {
            story = gson.fromJson(raw, Story.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException("LLM returned invalid JSON for story", e);
        }
        if (story == null || story.getScenes() == null
                || story.getScenes().size() != profile.getSceneCount()) {
            throw new IllegalStateException("LLM returned "
                    + (story == null || story.getScenes() == null ? 0 : story.getScenes().size())
                    + " scenes, expected " + profile.getSceneCount());
        }
        if (story.getHookText() == null || story.getHookText().isBlank()) {
            throw new IllegalStateException("Story missing hookText (on-screen hook)");
        }
        for (int i = 0; i < story.getScenes().size(); i++) {
            if (story.getScenes().get(i).effectivePrompts().isEmpty()) {
                throw new IllegalStateException(
                        "Scene " + (i + 1) + " has no image prompts");
            }
        }
        for (int i = 0; i < story.getScenes().size(); i++) {
            story.getScenes().get(i).setIndex(i + 1);
        }
        story.setStylePrefix(profile.getStylePrefix());
        return story;
    }
}
