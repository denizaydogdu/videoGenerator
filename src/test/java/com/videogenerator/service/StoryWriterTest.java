package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.TestProfiles;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.Story;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StoryWriterTest {
    public static final String LLM_JSON = """
        {"title":"The Lighthouse Keeper Who Vanished",
         "hookText":"Locked from the inside.",
         "scenes":[
           {"narration":"In 1972, a keeper disappeared.","imagePrompt":"abandoned lighthouse at dusk"},
           {"narration":"His logbook ended mid-sentence.","imagePrompt":"open logbook on wooden desk"},
           {"narration":"The door was locked from inside.","imagePrompt":"rusted iron door bolt close-up"}
         ]}""";

    @Test
    void parsesScenesAndAssignsIndexes() throws Exception {
        LlmClient fake = (sys, user) -> LLM_JSON;
        ChannelProfile p = TestProfiles.withSceneCount(3);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished keeper");

        Story story = new StoryWriter(fake).write(idea, p);

        assertEquals(3, story.getScenes().size());
        assertEquals(1, story.getScenes().get(0).getIndex());
        assertEquals("open logbook on wooden desk", story.getScenes().get(1).getImagePrompt());
        assertEquals(p.getStylePrefix(), story.getStylePrefix());
    }

    @Test
    void stripsMarkdownFences() throws Exception {
        LlmClient fake = (sys, user) -> "```json\n" + LLM_JSON + "\n```";
        Story story = new StoryWriter(fake).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        assertEquals("The Lighthouse Keeper Who Vanished", story.getTitle());
    }

    @Test
    void rejectsWrongSceneCount() {
        LlmClient fake = (sys, user) -> LLM_JSON; // 3 sahne döner
        ChannelProfile p = TestProfiles.withSceneCount(6);
        assertThrows(IllegalStateException.class,
            () -> new StoryWriter(fake).write(new ContentIdea(), p));
    }
}
