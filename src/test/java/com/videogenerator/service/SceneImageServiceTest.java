package com.videogenerator.service;

import com.videogenerator.api.ImageGenerator;
import com.videogenerator.model.Story;
import com.videogenerator.model.StoryScene;
import com.videogenerator.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneImageServiceTest {
    private Story storyWith(int n) {
        Story s = new Story();
        s.setStylePrefix("film grain, no faces");
        List<StoryScene> scenes = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            StoryScene sc = new StoryScene();
            sc.setIndex(i);
            sc.setImagePrompt("prompt " + i);
            scenes.add(sc);
        }
        s.setScenes(scenes);
        return s;
    }

    @Test
    void prependsStyleSkipsExistingAndTracksCost(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("01.png"), "already-here"); // sahne 1 mevcut
        List<String> prompts = new ArrayList<>();
        ImageGenerator fake = (prompt, out) -> {
            prompts.add(prompt);
            try {
                Files.writeString(out, "png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return out.toFile();
        };
        Story story = storyWith(3);
        double cost = new SceneImageService(fake).generateAll(story, dir);

        assertEquals(2, prompts.size()); // 1 atlandı
        assertTrue(prompts.get(0).startsWith("film grain, no faces, "));
        assertEquals("scenes/02.png", story.getScenes().get(1).getImageFile());
        assertEquals(2 * Constants.COST_IMAGE_MEDIUM, cost, 1e-9);
    }
}
