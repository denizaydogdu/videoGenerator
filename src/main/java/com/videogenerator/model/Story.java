package com.videogenerator.model;

import java.util.List;

/**
 * Language-independent story: title, style lock and ordered scenes.
 */
public class Story {
    private String title;
    private String stylePrefix;
    private List<StoryScene> scenes;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStylePrefix() {
        return stylePrefix;
    }

    public void setStylePrefix(String stylePrefix) {
        this.stylePrefix = stylePrefix;
    }

    public List<StoryScene> getScenes() {
        return scenes;
    }

    public void setScenes(List<StoryScene> scenes) {
        this.scenes = scenes;
    }
}
