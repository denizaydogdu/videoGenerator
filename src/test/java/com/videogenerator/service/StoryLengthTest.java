package com.videogenerator.service;

import com.videogenerator.api.LlmClient;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.TestProfiles;
import com.videogenerator.model.ContentIdea;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Süre disiplini: 75 sn hedefli profil, prompt'a açık bir kelime bütçesi
 * koymalı — ilk gerçek koşuda senaryolar hedefi %25-55 aştı (93-116 sn).
 */
class StoryLengthTest {
    @Test
    void promptCarriesExplicitWordBudget() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        LlmClient fake = (sys, user) -> {
            captured.set(user);
            return StoryWriterTest.LLM_JSON;
        };
        ChannelProfile p = TestProfiles.withSceneCount(3); // targetDuration 75
        new StoryWriter(fake).write(new ContentIdea(), p);

        // 75 sn × 2.2 kelime/sn = 165 kelime toplam bütçe
        assertTrue(captured.get().contains("165 words"),
                "prompt kelime bütçesi içermeli:\n" + captured.get());
        assertTrue(captured.get().contains("55 words"),
                "sahne başına bütçe (165/3) belirtilmeli");
    }
}
