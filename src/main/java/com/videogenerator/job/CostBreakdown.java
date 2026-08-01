package com.videogenerator.job;

/**
 * Per-job API cost accounting in USD.
 */
public class CostBreakdown {
    private double images;
    private double tts;
    private double music;
    private double llm;

    public double total() {
        return images + tts + music + llm;
    }

    public double getImages() {
        return images;
    }

    public void setImages(double images) {
        this.images = images;
    }

    public double getTts() {
        return tts;
    }

    public void setTts(double tts) {
        this.tts = tts;
    }

    public double getMusic() {
        return music;
    }

    public void setMusic(double music) {
        this.music = music;
    }

    public double getLlm() {
        return llm;
    }

    public void setLlm(double llm) {
        this.llm = llm;
    }
}
