package com.videogenerator.processor;

import com.videogenerator.model.Alignment;
import com.videogenerator.model.SubtitleCue;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubtitleRendererTest {
    private Alignment fixture() { // "Hi. Bye." — SceneTimerTest ile aynı
        Alignment a = new Alignment();
        a.setCharacters(List.of("H", "i", ".", " ", "B", "y", "e", "."));
        a.setCharacterStartTimesSeconds(List.of(0.0, 0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68));
        a.setCharacterEndTimesSeconds(List.of(0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68, 0.80));
        return a;
    }

    @Test
    void groupsWordsIntoCues() {
        List<SubtitleCue> cues = SubtitleRenderer.buildCues(fixture(), 1);
        assertEquals(2, cues.size());
        assertEquals("Hi.", cues.get(0).getText());
        assertEquals(0.0, cues.get(0).getStart(), 1e-9);
        assertEquals(0.24, cues.get(0).getEnd(), 1e-9);
        assertEquals("Bye.", cues.get(1).getText());
        assertEquals(0.30, cues.get(1).getStart(), 1e-9);
    }

    @Test
    void assContainsHeaderAndDialogue() {
        String ass = SubtitleRenderer.toAss(SubtitleRenderer.buildCues(fixture(), 2));
        assertTrue(ass.contains("PlayResX: 1080"));
        assertTrue(ass.contains("PlayResY: 1920"));
        assertTrue(ass.contains("Dialogue: 0,0:00:00.00,0:00:00.80,Default"));
        assertTrue(ass.contains("Hi. Bye."));
    }

    @Test
    void assTimeFormatsHoursMinutes() {
        assertEquals("0:01:05.50", SubtitleRenderer.assTime(65.5));
        assertEquals("1:00:00.00", SubtitleRenderer.assTime(3600.0));
    }

    @Test
    void assTimeCarriesCentisecondRounding() {
        assertEquals("0:01:00.00", SubtitleRenderer.assTime(59.995));
        assertEquals("0:02:00.00", SubtitleRenderer.assTime(119.996));
    }

    @Test
    void karaokeEmitsPerWordEventsWithAccentHighlight() {
        // "Hi. Bye." → 2 kelime → kelime başına 1 Dialogue; aktif kelime sarı.
        // Tasarım: event, SONRAKİ kelimenin başlangıcına kadar sürer (metin
        // aradaki boşlukta kaybolmaz); son kelime kendi bitişinde biter.
        String ass = SubtitleRenderer.toKaraokeAss(fixture(), 3, null);
        long dialogues = ass.lines().filter(l -> l.startsWith("Dialogue: 0,")).count();
        assertEquals(2, dialogues, "kelime başına bir event olmalı:\n" + ass);
        assertTrue(ass.contains("Dialogue: 0,0:00:00.00,0:00:00.30,Default"), ass);
        assertTrue(ass.contains("{\\1c&H00D7FF&}Hi.{\\1c&HFFFFFF&} Bye."), ass);
        assertTrue(ass.contains("Dialogue: 0,0:00:00.30,0:00:00.80,Default"), ass);
        assertTrue(ass.contains("Hi. {\\1c&H00D7FF&}Bye.{\\1c&HFFFFFF&}"), ass);
    }

    @Test
    void karaokeKeepsHookOverlay() {
        String ass = SubtitleRenderer.toKaraokeAss(fixture(), 3, "ZERO BODIES FOUND");
        assertTrue(ass.contains("Style: Hook"));
        assertTrue(ass.contains("ZERO BODIES FOUND"));
    }

    @Test
    void hookOverlayRendersTopCenteredForFirstSeconds() {
        String ass = SubtitleRenderer.toAss(
                SubtitleRenderer.buildCues(fixture(), 2), "FIVE KIDS. ZERO BODIES.");
        assertTrue(ass.contains("Style: Hook"), "ayrı Hook stili olmalı");
        assertTrue(ass.contains("Dialogue: 1,0:00:00.00,0:00:02.20,Hook"),
                "hook ilk ~2 saniyede ayrı katmanda gösterilmeli:\n" + ass);
        assertTrue(ass.contains("FIVE KIDS. ZERO BODIES."));
    }

    @Test
    void nullHookProducesNoHookEvents() {
        String ass = SubtitleRenderer.toAss(SubtitleRenderer.buildCues(fixture(), 2), null);
        assertFalse(ass.contains("Style: Hook"));
    }

    @Test
    void escapesAssControlCharacters() {
        assertEquals("a/N b (x)", SubtitleRenderer.escapeAssText("a\\N b {x}"));
    }
}
