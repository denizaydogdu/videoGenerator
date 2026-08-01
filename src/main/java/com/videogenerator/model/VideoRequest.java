package com.videogenerator.model;

/**
 * Request model for OpenAI Sora video generation
 */
public class VideoRequest {
    private String model;
    private String prompt;
    private int duration;
    private String aspectRatio;
    private String resolution;

    public VideoRequest() {
        this.model = "sora-1.0";
        this.duration = 15; // 15 seconds
        this.aspectRatio = "9:16"; // Vertical for YouTube Shorts
        this.resolution = "720p";
    }

    public VideoRequest(String prompt) {
        this();
        this.prompt = prompt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
