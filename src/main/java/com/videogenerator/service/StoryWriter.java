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

    public Story write(ContentIdea idea, ChannelProfile profile) throws ApiException {
        String system = "You write scripts for short vertical documentary videos. "
                + "Respond with ONLY valid JSON, no markdown fences.";
        String user = String.format("""
                Topic: %s
                Niche: %s
                Write a gripping %d-second story in English split into EXACTLY %d scenes.
                Each scene: 1-2 spoken sentences ("narration") and one visual description
                ("imagePrompt") showing PLACES, OBJECTS, DOCUMENTS or SILHOUETTES - never a
                recognizable human face. JSON shape:
                {"title": "...", "scenes":[{"narration":"...","imagePrompt":"..."}]}""",
                idea.getTitle(), profile.getNiche().getTopic(),
                profile.getTargetDurationSeconds(), profile.getSceneCount());

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
        for (int i = 0; i < story.getScenes().size(); i++) {
            story.getScenes().get(i).setIndex(i + 1);
        }
        story.setStylePrefix(profile.getStylePrefix());
        logger.info("Story written: {} ({} scenes)", story.getTitle(), story.getScenes().size());
        return story;
    }
}
