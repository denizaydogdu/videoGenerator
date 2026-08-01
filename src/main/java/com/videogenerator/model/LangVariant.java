package com.videogenerator.model;

import java.util.List;

/**
 * One language variant of a job: localized audio, subtitles, render and metadata.
 * File paths are relative to the job directory.
 */
public class LangVariant {
    private String lang;
    private VideoMetadata metadata;
    private String audioFile;
    private String alignmentFile;
    private String renderFile;
    private double durationSeconds;
    private List<Publication> publications;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public VideoMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(VideoMetadata metadata) {
        this.metadata = metadata;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public String getAlignmentFile() {
        return alignmentFile;
    }

    public void setAlignmentFile(String alignmentFile) {
        this.alignmentFile = alignmentFile;
    }

    public String getRenderFile() {
        return renderFile;
    }

    public void setRenderFile(String renderFile) {
        this.renderFile = renderFile;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<Publication> getPublications() {
        return publications;
    }

    public void setPublications(List<Publication> publications) {
        this.publications = publications;
    }
}
