package com.videogenerator.channel;

import java.util.List;

/**
 * Niche definition for a channel profile.
 */
public class NicheSpec {
    private String topic;
    private List<String> keywords;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
