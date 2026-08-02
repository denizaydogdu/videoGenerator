package com.videogenerator.web;

import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatsCollectorTest {

    private Job jobWith(String lang, String... platformUrls) {
        Job job = Job.create("ch1");
        LangVariant v = new LangVariant();
        v.setLang(lang);
        v.setPublications(new ArrayList<>());
        for (int i = 0; i < platformUrls.length; i += 2) {
            Publication p = new Publication();
            p.setPlatform(platformUrls[i]);
            p.setStatus("PUBLISHED");
            p.setUrl(platformUrls[i + 1]);
            v.getPublications().add(p);
        }
        job.getVariants().add(v);
        return job;
    }

    @Test
    void extractsYoutubeIdAndFbReelId() {
        assertEquals("oHECwZxIveY",
                StatsCollector.youtubeId("https://www.youtube.com/watch?v=oHECwZxIveY"));
        assertEquals("1429410409008296",
                StatsCollector.fbReelId("https://www.facebook.com/reel/1429410409008296"));
        assertNull(StatsCollector.youtubeId("https://www.instagram.com/reel/X/"));
    }

    @Test
    void collectsRowsAndToleratesProviderFailure() {
        Job job = jobWith("en",
                "YOUTUBE", "https://www.youtube.com/watch?v=abc",
                "INSTAGRAM", "https://www.instagram.com/reel/XYZ/",
                "FACEBOOK", "https://www.facebook.com/reel/123");
        StatsCollector collector = new StatsCollector(Map.of(
                "YOUTUBE", url -> 100L,
                "INSTAGRAM", url -> { throw new RuntimeException("api down"); },
                "FACEBOOK", url -> 5L));

        List<StatsCollector.Row> rows = collector.collect(job);

        assertEquals(3, rows.size());
        assertEquals(100L, rows.get(0).views());
        assertNull(rows.get(1).views(), "sağlayıcı hatası satırı düşürmemeli");
        assertEquals(5L, rows.get(2).views());
        assertEquals("en", rows.get(0).lang());
    }

    @Test
    void skipsNonPublishedAndUnknownPlatforms() {
        Job job = jobWith("en", "TIKTOK", "https://tiktok.com/x");
        Publication skipped = new Publication();
        skipped.setPlatform("YOUTUBE");
        skipped.setStatus("SKIPPED_NO_PUBLISHER");
        skipped.setUrl(null);
        job.getVariants().get(0).getPublications().add(skipped);

        List<StatsCollector.Row> rows = new StatsCollector(Map.of(
                "YOUTUBE", url -> 1L)).collect(job);

        assertTrue(rows.isEmpty());
    }
}
