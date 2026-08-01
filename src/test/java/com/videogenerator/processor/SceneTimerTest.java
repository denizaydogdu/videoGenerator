package com.videogenerator.processor;

import com.videogenerator.model.Alignment;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneTimerTest {
    // "Hi. Bye." → sahneler: ["Hi.", "Bye."], birleşik metin 8 karakter
    private Alignment fixture() {
        Alignment a = new Alignment();
        a.setCharacters(List.of("H", "i", ".", " ", "B", "y", "e", "."));
        a.setCharacterStartTimesSeconds(List.of(0.0, 0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68));
        a.setCharacterEndTimesSeconds(List.of(0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68, 0.80));
        return a;
    }

    @Test
    void cutsAtLastCharOfEachScene() {
        double[] ends = SceneTimer.sceneEndTimes(List.of("Hi.", "Bye."), fixture());
        assertArrayEquals(new double[]{0.24, 0.80}, ends, 1e-9);
        assertArrayEquals(new double[]{0.24, 0.56}, SceneTimer.sceneDurations(ends), 1e-9);
    }

    @Test
    void singleSceneEndsAtTotal() {
        double[] ends = SceneTimer.sceneEndTimes(List.of("Hi. Bye."), fixture());
        assertArrayEquals(new double[]{0.80}, ends, 1e-9);
    }

    @Test
    void joinMatchesCharacterCount() {
        String joined = SceneTimer.joinNarrations(List.of("Hi.", "Bye."));
        assertEquals(fixture().length(), joined.length());
    }

    @Test
    void toleratesAlignmentShorterThanText() {
        // Normalizasyon karakter sayısını değiştirebilir; indeks taşmamalı
        double[] ends = SceneTimer.sceneEndTimes(
            List.of("Hi.", "Bye now longer text."), fixture());
        assertEquals(0.80, ends[1], 1e-9); // son sahne daima toplam süreye uzar
    }
}
