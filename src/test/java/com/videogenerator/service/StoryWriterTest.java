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
    void retriesOnceWhenLlmReturnsWrongSceneCount() throws Exception {
        // Canlı hata (2026-08-02): GPT sahneleri tek objeye tekrarlanan
        // anahtarlar olarak yazdı -> 1 sahne. Doğrulama hatası retry'lanmalı.
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        LlmClient flaky = (sys, user) -> calls.incrementAndGet() == 1
                ? "{\"title\":\"x\",\"hookText\":\"h\",\"scenes\":[{\"narration\":\"a\",\"imagePrompt\":\"p\"}]}"
                : LLM_JSON;
        ChannelProfile p = TestProfiles.withSceneCount(3);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished keeper");

        Story story = new StoryWriter(flaky).write(idea, p);

        assertEquals(3, story.getScenes().size());
        assertEquals(2, calls.get(), "must retry after validation failure");
    }

    @Test
    void givesUpAfterThreeFailedAttempts() {
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        LlmClient bad = (sys, user) -> {
            calls.incrementAndGet();
            return "{\"title\":\"x\",\"hookText\":\"h\",\"scenes\":[]}";
        };
        ChannelProfile p = TestProfiles.withSceneCount(3);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished keeper");

        assertThrows(IllegalStateException.class,
                () -> new StoryWriter(bad).write(idea, p));
        assertEquals(3, calls.get(), "exactly three attempts");
    }

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
