package com.videogenerator.model;

import java.util.List;

/**
 * One language's rendering input: localized narrations (scene order
 * preserved) plus platform metadata in the same language.
 */
public class LocalizedStory {
    private List<String> narrations;
    private String hookText; // ekran hook yazısının hedef dildeki hali
    private VideoMetadata metadata;

    public String getHookText() {
        return hookText;
    }

    public void setHookText(String hookText) {
        this.hookText = hookText;
    }

    public List<String> getNarrations() {
        return narrations;
    }

    public void setNarrations(List<String> narrations) {
        this.narrations = narrations;
    }

    public VideoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(VideoMetadata metadata) {
        this.metadata = metadata;
    }
}
