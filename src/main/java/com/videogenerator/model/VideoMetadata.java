package com.videogenerator.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Video metadata including title, description, and hashtags
 */
public class VideoMetadata {
    private String title;
    private String description;

    @SerializedName("hashtags")
    private List<String> hashtags;

    private String category;

    @SerializedName("generated_at")
    private long generatedAt;

    public VideoMetadata() {
        this.generatedAt = System.currentTimeMillis();
    }

    public VideoMetadata(String title, String description, List<String> hashtags) {
        this();
        this.title = title;
        this.description = description;
        this.hashtags = hashtags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getHashtags() {
        return hashtags;
    }

    public void setHashtags(List<String> hashtags) {
        this.hashtags = hashtags;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(long generatedAt) {
        this.generatedAt = generatedAt;
    }

    /**
     * Gets hashtags as a single string (space-separated)
     */
    public String getHashtagsAsString() {
        if (hashtags == null || hashtags.isEmpty()) {
            return "";
        }
        return String.join(" ", hashtags);
    }

    /**
     * Gets YouTube-formatted tags (comma-separated, without # symbol)
     */
    public String[] getYouTubeTags() {
        if (hashtags == null || hashtags.isEmpty()) {
            return new String[0];
        }
        return hashtags.stream()
                .map(tag -> tag.startsWith("#") ? tag.substring(1) : tag)
                .toArray(String[]::new);
    }

    /**
     * Validates metadata fields
     */
    public boolean isValid() {
        return title != null && !title.isEmpty() &&
                title.length() <= 100 &&
                description != null && !description.isEmpty() &&
                description.length() <= 5000 &&
                hashtags != null && !hashtags.isEmpty() &&
                hashtags.size() <= 15;
    }

    @Override
    public String toString() {
        return "VideoMetadata{" +
                "title='" + title + '\'' +
                ", description length=" + (description != null ? description.length() : 0) +
                ", hashtags=" + (hashtags != null ? hashtags.size() : 0) +
                '}';
    }
}
