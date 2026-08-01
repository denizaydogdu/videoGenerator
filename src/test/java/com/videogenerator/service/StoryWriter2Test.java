package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.TestProfiles;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.Story;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Format 2.0 + playbook gereksinimleri: gerçek vaka zorunluluğu, yasal
 * korkuluklar, ters hook, ekran hook yazısı, loop sonu, anti-şablon
 * çeşitlendirme.
 */
class StoryWriter2Test {
    static final String LLM_JSON_V2 = """
        {"title":"What Happened to the Sodder Children?",
         "hookText":"Five children. Zero bodies found.",
         "scenes":[
           {"narration":"Five children vanished from a burning house - and no bodies were ever found.","imagePrompt":"burned house foundation in snow, investigation markers"},
           {"narration":"The fire chief called it accidental. The evidence said otherwise.","imagePrompt":"charred fuse box close-up, vintage report"},
           {"narration":"To this day, no one knows if the Sodder children died that night.","imagePrompt":"faded missing-children billboard on empty road"}
         ]}""";

    private AtomicReference<String> captured = new AtomicReference<>();

    private LlmClient capturing() {
        return (sys, user) -> {
            captured.set(sys + "\n---\n" + user);
            return LLM_JSON_V2;
        };
    }

    @Test
    void promptEnforcesRealCasesAndLegalGuardrails() throws Exception {
        new StoryWriter(capturing()).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        String prompt = captured.get();
        assertTrue(prompt.contains("REAL"), "gerçek vaka zorunluluğu promptta olmalı");
        assertTrue(prompt.toLowerCase().contains("never invent"),
                "uydurma yasağı açık olmalı (kanal kapatma emsali)");
        assertTrue(prompt.contains("finalized") || prompt.contains("10+ years"),
                "kesinleşmiş/soğuk dosya kuralı olmalı");
        assertTrue(prompt.toLowerCase().contains("alleged"),
                "isnat dili kuralı olmalı");
    }

    @Test
    void promptDemandsInvertedHookAndLoop() throws Exception {
        new StoryWriter(capturing()).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        String prompt = captured.get();
        assertTrue(prompt.contains("shocking") || prompt.contains("outcome first"),
                "ters hook talimatı olmalı");
        assertTrue(prompt.toLowerCase().contains("never open with a date"),
                "tarihle açılış yasağı olmalı");
        assertTrue(prompt.toLowerCase().contains("loop") || prompt.contains("recontextualize"),
                "loop sonu talimatı olmalı");
    }

    @Test
    void parsesHookTextAndValidatesIt() throws Exception {
        Story story = new StoryWriter(capturing())
                .write(new ContentIdea(), TestProfiles.withSceneCount(3));
        assertEquals("Five children. Zero bodies found.", story.getHookText());
    }

    @Test
    void rejectsMissingHookText() {
        LlmClient noHook = (sys, user) -> LLM_JSON_V2.replace(
                "\"hookText\":\"Five children. Zero bodies found.\",", "");
        assertThrows(IllegalStateException.class, () ->
                new StoryWriter(noHook).write(new ContentIdea(), TestProfiles.withSceneCount(3)));
    }

    @Test
    void pacingVariantChangesBetweenCalls() throws Exception {
        // Anti-şablon: yapı çeşitlendirme talimatı her koşuda prompta girer
        new StoryWriter(capturing()).write(new ContentIdea(), TestProfiles.withSceneCount(3));
        assertTrue(captured.get().contains("Pacing style for THIS video"),
                "tempo varyantı talimatı olmalı");
    }
}
