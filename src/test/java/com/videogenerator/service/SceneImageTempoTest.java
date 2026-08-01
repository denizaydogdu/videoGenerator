package com.videogenerator.service;

import com.videogenerator.api.ImageGenerator;
import com.videogenerator.api.LlmClient;
import com.videogenerator.channel.TestProfiles;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.Story;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F2 — Görsel tempo: sahne başına 2 görsel → her 3-6 sn'de görüntü değişimi.
 * StoryWriter her sahne için 2 prompt üretir; SceneImageService ikisini de
 * üretir; render sahne süresini görselleri arasında bölüştürür.
 */
class SceneImageTempoTest {
    static final String LLM_JSON_TWO_IMAGES = """
        {"title":"What Happened to the Sodder Children?",
         "hookText":"Five children. Zero bodies.",
         "scenes":[
           {"narration":"Five children vanished from a burning house.",
            "imagePrompts":["burned house foundation in snow","charred staircase remains close-up"]},
           {"narration":"The fire chief called it accidental.",
            "imagePrompts":["vintage fire report document","old fuse box with cut wires"]},
           {"narration":"No one knows if they died that night.",
            "imagePrompts":["faded missing-children billboard","empty road at dusk"]}
         ]}""";

    @Test
    void storyWriterParsesTwoImagePromptsPerScene() throws Exception {
        LlmClient fake = (sys, user) -> LLM_JSON_TWO_IMAGES;
        Story story = new StoryWriter(fake).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        assertEquals(2, story.getScenes().get(0).getImagePrompts().size());
        assertEquals("charred staircase remains close-up",
                story.getScenes().get(0).getImagePrompts().get(1));
    }

    @Test
    void promptAsksForTwoImagesPerScene() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (sys, user) -> { captured.set(user); return LLM_JSON_TWO_IMAGES; };
        new StoryWriter(fake).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        assertTrue(captured.get().contains("TWO visual descriptions"),
                "sahne başına 2 görsel talimatı olmalı");
    }

    @Test
    void serviceGeneratesAllImagesWithSubIndexNaming(@TempDir Path dir) throws Exception {
        LlmClient fake = (sys, user) -> LLM_JSON_TWO_IMAGES;
        Story story = new StoryWriter(fake).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        List<String> prompts = new ArrayList<>();
        ImageGenerator gen = (prompt, out) -> {
            prompts.add(prompt);
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        new SceneImageService(gen).generateAll(story, dir);

        assertEquals(6, prompts.size(), "3 sahne x 2 görsel");
        assertTrue(Files.exists(dir.resolve("01a.png")));
        assertTrue(Files.exists(dir.resolve("01b.png")));
        assertTrue(Files.exists(dir.resolve("03b.png")));
        assertEquals(List.of("scenes/01a.png", "scenes/01b.png"),
                story.getScenes().get(0).getImageFiles());
    }

    @Test
    void legacySingleImagePromptStillWorks(@TempDir Path dir) throws Exception {
        // Eski job.json'lar (imagePrompt tekil) resume'da kırılmamalı
        LlmClient fake = (sys, user) -> StoryWriterTest.LLM_JSON;
        Story story = new StoryWriter(fake).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        ImageGenerator gen = (prompt, out) -> {
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        new SceneImageService(gen).generateAll(story, dir);
        assertFalse(story.getScenes().get(0).getImageFiles().isEmpty());
    }
}
