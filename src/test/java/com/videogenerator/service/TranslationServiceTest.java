package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.model.LocalizedStory;
import com.videogenerator.model.Story;
import com.videogenerator.model.StoryScene;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TranslationServiceTest {
    static final String TR_JSON = """
        {"narrations":["1972'de bir bekçi kayboldu.","Seyir defteri yarım kaldı."],
         "hookText":"İçeriden kilitliydi.",
         "metadata":{"title":"Kaybolan Fener Bekçisi","description":"Gerçek bir gizem.",
                     "hashtags":["#gizem","#gerçeksuç"]}}""";

    private Story storyWithScenes(String... narrations) {
        Story story = new Story();
        story.setTitle("The Lighthouse Keeper Who Vanished");
        story.setScenes(java.util.Arrays.stream(narrations).map(n -> {
            StoryScene s = new StoryScene();
            s.setNarration(n);
            return s;
        }).toList());
        return story;
    }

    @Test
    void localizesNarrationsAndMetadata() throws Exception {
        Story story = storyWithScenes("In 1972, a keeper disappeared.",
                "His logbook ended mid-sentence.");
        LlmClient fake = (sys, user) -> TR_JSON;
        LocalizedStory loc = new TranslationService(fake).localize(story, "tr");

        assertEquals(2, loc.getNarrations().size());
        assertEquals("Kaybolan Fener Bekçisi", loc.getMetadata().getTitle());
        assertEquals("İçeriden kilitliydi.", loc.getHookText());
        assertEquals(List.of("#gizem", "#gerçeksuç"), loc.getMetadata().getHashtags());
    }

    @Test
    void promptCarriesTitleFormulaBank() throws Exception {
        // F6 — dil başına kazanan başlık kalıpları prompta girer
        java.util.concurrent.atomic.AtomicReference<String> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        LlmClient fake = (sys, user) -> { captured.set(user); return TR_JSON; };
        new TranslationService(fake).localize(
                storyWithScenes("one", "two"), "tr");
        assertTrue(captured.get().contains("kimse çözemedi")
                        || captured.get().contains("Polis bile"),
                "TR başlık kalıpları prompta girmeli:\n" + captured.get());
    }

    @Test
    void descriptionGetsLegalDisclaimerFooter() throws Exception {
        // F6 — yasal dipnot: kamu kayıtlarına dayanma notu otomatik eklenir
        LlmClient fake = (sys, user) -> TR_JSON;
        LocalizedStory loc = new TranslationService(fake)
                .localize(storyWithScenes("one", "two"), "tr");
        assertTrue(loc.getMetadata().getDescription().contains("kamuya yansımış"),
                "TR dipnot eklenmiş olmalı: " + loc.getMetadata().getDescription());
    }

    @Test
    void rejectsEmptyMetadata() {
        Story story = storyWithScenes("one", "two");
        LlmClient fake = (sys, user) ->
            "{\"narrations\":[\"a\",\"b\"],\"metadata\":{}}";
        assertThrows(IllegalStateException.class,
            () -> new TranslationService(fake).localize(story, "tr"));
    }

    @Test
    void rejectsNarrationCountMismatch() {
        Story story = storyWithScenes("one"); // 1 sahne, fake 2 döndürüyor
        LlmClient fake = (sys, user) -> TR_JSON;
        assertThrows(IllegalStateException.class,
            () -> new TranslationService(fake).localize(story, "tr"));
    }
}
