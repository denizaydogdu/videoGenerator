package com.videogenerator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: builds an Alignment giving every character 0.1s.
 */
public final class FakeAlignments {
    private FakeAlignments() {
    }

    public static Alignment forText(String text) {
        List<String> chars = new ArrayList<>();
        List<Double> starts = new ArrayList<>();
        List<Double> ends = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            chars.add(String.valueOf(text.charAt(i)));
            starts.add(i * 0.1);
            ends.add((i + 1) * 0.1);
        }
        Alignment a = new Alignment();
        a.setCharacters(chars);
        a.setCharacterStartTimesSeconds(starts);
        a.setCharacterEndTimesSeconds(ends);
        return a;
    }
}
