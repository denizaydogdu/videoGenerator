package com.videogenerator.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Represents a viral content idea for YouTube Shorts.
 * Contains SEO-optimized title, emotional hook, and engagement metrics.
 */
public class ContentIdea {
    private Integer id;
    private Integer nicheId;
    private String title;            // SEO-optimized title (max 100 chars)
    private String hook;             // Emotional hook for first 3 seconds
    private String description;      // Brief description (100-150 chars)
    private List<String> hashtags;   // 5-10 relevant hashtags
    private double estimatedCtr;     // Estimated click-through rate (0.0 to 1.0)
    private LocalDateTime createdDate;

    public ContentIdea() {
        this.createdDate = LocalDateTime.now();
    }

    public ContentIdea(String title, String hook) {
        this.title = title;
        this.hook = hook;
        this.createdDate = LocalDateTime.now();
    }

    public ContentIdea(Integer nicheId, String title, String hook, String description,
                      List<String> hashtags, double estimatedCtr) {
        this.nicheId = nicheId;
        this.title = title;
        this.hook = hook;
        this.description = description;
        this.hashtags = hashtags;
        this.estimatedCtr = estimatedCtr;
        this.createdDate = LocalDateTime.now();
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getNicheId() {
        return nicheId;
    }

    public void setNicheId(Integer nicheId) {
        this.nicheId = nicheId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHook() {
        return hook;
    }

    public void setHook(String hook) {
        this.hook = hook;
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

    public double getEstimatedCtr() {
        return estimatedCtr;
    }

    public void setEstimatedCtr(double estimatedCtr) {
        this.estimatedCtr = estimatedCtr;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    /**
     * Gets hashtags as a formatted string for YouTube.
     *
     * @return space-separated hashtags with # prefix
     */
    public String getHashtagsAsString() {
        if (hashtags == null || hashtags.isEmpty()) {
            return "";
        }
        return hashtags.stream()
            .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
            .reduce((a, b) -> a + " " + b)
            .orElse("");
    }

    /**
     * Validates that the title meets YouTube Shorts requirements.
     *
     * @return true if title is valid
     */
    public boolean isTitleValid() {
        return title != null && title.length() > 0 && title.length() <= 100;
    }

    /**
     * Checks if this idea has high viral potential.
     *
     * @param threshold minimum CTR threshold (0.0 to 1.0)
     * @return true if estimated CTR is above threshold
     */
    public boolean hasHighViralPotential(double threshold) {
        return estimatedCtr >= threshold;
    }

    /**
     * Gets the complete video description with hashtags.
     *
     * @return formatted description
     */
    public String getFullDescription() {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isEmpty()) {
            sb.append(description);
        }
        if (hashtags != null && !hashtags.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(getHashtagsAsString());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentIdea that = (ContentIdea) o;
        return Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title);
    }

    @Override
    public String toString() {
        return String.format(
            "ContentIdea{title='%s', hook='%s', ctr=%.2f, hashtags=%d}",
            title, hook, estimatedCtr, hashtags != null ? hashtags.size() : 0
        );
    }
}
