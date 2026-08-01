package com.videogenerator.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for OpenAI Sora video generation
 */
public class VideoResponse {
    private String id;
    private String object;
    private String status;

    @SerializedName("video_url")
    private String videoUrl;

    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    private String prompt;
    private double duration;
    private int width;
    private int height;

    @SerializedName("aspect_ratio")
    private String aspectRatio;

    @SerializedName("created_at")
    private long createdAt;

    @SerializedName("expires_at")
    private long expiresAt;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "pending".equalsIgnoreCase(status) || "processing".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status);
    }
}
