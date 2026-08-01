package com.videogenerator.service;

import com.videogenerator.api.ImageGenerator;
import com.videogenerator.model.Story;
import com.videogenerator.model.StoryScene;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates one image per scene, prefixing every prompt with the channel's
 * style lock. Existing files are skipped so an interrupted job resumes
 * without paying for the same image twice. This class is the ONLY place
 * that applies the style prefix (single responsibility, no double-prefix).
 */
public class SceneImageService {
    private static final Logger logger = LoggerFactory.getLogger(SceneImageService.class);
    private final ImageGenerator generator;

    public SceneImageService(ImageGenerator generator) {
        this.generator = generator;
    }

    /**
     * @return total cost in USD of the images actually generated
     */
    public double generateAll(Story story, Path scenesDir) throws ApiException, IOException {
        Files.createDirectories(scenesDir);
        double cost = 0;
        for (StoryScene scene : story.getScenes()) {
            String name = String.format("%02d.png", scene.getIndex());
            Path out = scenesDir.resolve(name);
            scene.setImageFile("scenes/" + name);
            if (Files.exists(out)) {
                logger.info("Skip existing scene image {}", name);
                continue;
            }
            generator.generate(story.getStylePrefix() + ", " + scene.getImagePrompt(), out);
            cost += Constants.COST_IMAGE_MEDIUM;
        }
        return cost;
    }
}
