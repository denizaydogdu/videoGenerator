package com.videogenerator.processor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KenBurnsFilterTest {
    @Test
    void twoScenesProduceZoompanXfadeAndSubtitles() {
        String g = KenBurnsRenderer.buildFilterGraph(new double[]{4.0, 3.0}, "subs/en.ass", 30);
        assertTrue(g.contains("zoompan=z='1+0.10*on/120'"), g);    // sahne 0: 4.0s*30fps, zoom-in
        assertTrue(g.contains("zoompan=z='1.10-0.10*on/105'"), g); // sahne 1: (3.0+0.5)*30, zoom-out
        assertTrue(g.contains("xfade=transition=fade:duration=0.5:offset=3.500"), g); // 4.0-0.5
        assertTrue(g.contains("subtitles=filename='subs/en.ass'"), g);
        assertTrue(g.endsWith("[vout]"), g);
    }

    @Test
    void singleSceneHasNoXfade() {
        String g = KenBurnsRenderer.buildFilterGraph(new double[]{5.0}, "s.ass", 30);
        assertFalse(g.contains("xfade"));
        assertTrue(g.contains("subtitles=filename='s.ass'"));
    }

    @Test
    void shortDurationsScaleXfadeDownNoNegativeOffsets() {
        // F2 sahne bölüşümü ile 0.8 sn'lik görseller mümkün — offset asla negatif olmamalı
        String g = KenBurnsRenderer.buildFilterGraph(
                new double[]{0.8, 0.8, 0.8}, "s.ass", 30);
        assertFalse(g.contains("offset=-"), "negatif xfade offset üretilmemeli:\n" + g);
        assertTrue(g.contains("duration=0.3"), "xfade kısa görsele ölçeklenmeli (0.8*0.4)");
    }

    @Test
    void totalChainLengthMatchesAudioLength() {
        // 3 sahne: zincir sonu = toplam anlatım süresi olmalı (ses kesilmez)
        double[] durations = {3.0, 4.0, 2.5};
        String g = KenBurnsRenderer.buildFilterGraph(durations, "s.ass", 30);
        // son xfade offset: (3.0) - 0.5 + (4.0+0.5) - 0.5 = 6.5
        assertTrue(g.contains("offset=6.500"), g);
    }
}
