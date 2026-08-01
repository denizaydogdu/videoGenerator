package com.videogenerator.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationDefaultsTest {
    @Test
    void newKeysHaveModernDefaults() {
        Configuration c = Configuration.getInstance();
        assertEquals("gpt-5.6-luna", c.getLlmModel());
        assertEquals("gpt-image-2", c.getImageModel());
        assertEquals("medium", c.getImageQuality());
        assertEquals("1024x1536", c.getImageSize());
        assertEquals("eleven_v3", c.getTtsModel());
        assertEquals("music_v2", c.getMusicModel());
        assertEquals(1080, c.getVideoWidth());
        assertEquals(1920, c.getVideoHeight());
        assertTrue(c.getMonthlyBudgetUsd() > 0);
        assertEquals(8080, c.getBackofficePort());
        assertEquals("channels", c.getChannelsDir());
        assertEquals("output/jobs", c.getJobsDir());
        assertEquals("output/costs", c.getCostsDir());
    }
}
