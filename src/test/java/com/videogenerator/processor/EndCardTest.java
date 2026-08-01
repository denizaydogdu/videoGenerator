package com.videogenerator.processor;

import com.videogenerator.model.Alignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F4b — Son-kart: sesli CTA yerine son ~2.5 sn'de görsel seri vaadi
 * ("YARIN YENİ DOSYA →"). Loop'u bozmaz, tamamlanmayı kesmez.
 */
class EndCardTest {
    private Alignment fixture() { // toplam 0.80 sn
        Alignment a = new Alignment();
        a.setCharacters(List.of("H", "i", ".", " ", "B", "y", "e", "."));
        a.setCharacterStartTimesSeconds(List.of(0.0, 0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68));
        a.setCharacterEndTimesSeconds(List.of(0.08, 0.16, 0.24, 0.30, 0.42, 0.55, 0.68, 0.80));
        return a;
    }

    @Test
    void endCardRendersInFinalSecondsBottomArea() {
        String ass = SubtitleRenderer.toKaraokeAss(fixture(), 3, null, "YARIN YENİ DOSYA →");
        assertTrue(ass.contains("Style: EndCard"), "ayrı EndCard stili olmalı:\n" + ass);
        // toplam 0.80 sn; son-kart max(0, 0.80-2.5)=0.00'dan 0.80'e
        assertTrue(ass.contains(",EndCard,"), ass);
        assertTrue(ass.contains("YARIN YENİ DOSYA →"), ass);
    }

    @Test
    void nullEndTextProducesNoEndCard() {
        String ass = SubtitleRenderer.toKaraokeAss(fixture(), 3, null, null);
        assertFalse(ass.contains("EndCard"));
    }
}
