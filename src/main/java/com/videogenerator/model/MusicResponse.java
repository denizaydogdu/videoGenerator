package com.videogenerator.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for Suno API music generation
 */
public class MusicResponse {
    private String id;
    private String status;

    @SerializedName("audio_url")
    private String audioUrl;

    @SerializedName("video_url")
    private String videoUrl;

    @SerializedName("image_url")
    private String imageUrl;

    private String title;
    private String lyric;
    private String prompt;
    private String type;
    private String tags;
    private double duration;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("model_name")
    private String modelName;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isCompleted() {
        return "SUCCESS".equalsIgnoreCase(status) || "COMPLETE".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "ERROR".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status) || "QUEUED".equalsIgnoreCase(status);
    }
}
