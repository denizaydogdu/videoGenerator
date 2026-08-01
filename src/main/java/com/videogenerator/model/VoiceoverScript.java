package com.videogenerator.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a voiceover script for YouTube Shorts.
 * Optimized for 60-second videos with hook, body, and CTA structure.
 */
public class VoiceoverScript {
    private String hook;              // First 3-5 seconds (15-25 words)
    private String body;              // Main content 45-50 seconds (100-130 words)
    private String callToAction;      // Final 5 seconds (10-15 words)
    private int targetDurationSeconds; // Target duration (typically 55-58 seconds)
    private int estimatedWordCount;    // Total word count
    private List<PauseMarker> pauseMarkers; // Pause markers for natural speech

    public VoiceoverScript() {
        this.targetDurationSeconds = 55;
        this.pauseMarkers = new ArrayList<>();
    }

    public VoiceoverScript(String hook, String body, String callToAction) {
        this();
        this.hook = hook;
        this.body = body;
        this.callToAction = callToAction;
        this.estimatedWordCount = countWords();
    }

    /**
     * Gets the complete script text with pause markers.
     *
     * @return full script
     */
    public String getFullScript() {
        StringBuilder script = new StringBuilder();

        if (hook != null && !hook.isEmpty()) {
            script.append(hook);
            script.append(" [PAUSE:0.5] ");
        }

        if (body != null && !body.isEmpty()) {
            script.append(body);
            script.append(" [PAUSE:0.3] ");
        }

        if (callToAction != null && !callToAction.isEmpty()) {
            script.append(callToAction);
        }

        return script.toString().trim();
    }

    /**
     * Gets the script without pause markers (for TTS).
     *
     * @return clean script text
     */
    public String getCleanScript() {
        return getFullScript().replaceAll("\\[PAUSE:\\d+\\.?\\d*\\]", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * Counts total words in the script.
     *
     * @return word count
     */
    private int countWords() {
        String cleanText = getCleanScript();
        if (cleanText.isEmpty()) {
            return 0;
        }
        return cleanText.split("\\s+").length;
    }

    /**
     * Validates that the script meets requirements.
     *
     * @return true if valid
     */
    public boolean isValid() {
        if (hook == null || hook.isEmpty()) {
            return false;
        }
        if (body == null || body.isEmpty()) {
            return false;
        }

        // Check word count (aim for 140-170 words for 55 seconds)
        int words = countWords();
        return words >= 120 && words <= 200;
    }

    /**
     * Estimates duration based on word count.
     * Assumes average speaking rate of 2.5 words per second.
     *
     * @return estimated duration in seconds
     */
    public int getEstimatedDuration() {
        int words = countWords();
        double wordsPerSecond = 2.5;
        return (int) Math.ceil(words / wordsPerSecond);
    }

    /**
     * Checks if script fits within target duration.
     *
     * @return true if duration is acceptable
     */
    public boolean fitsTargetDuration() {
        int estimated = getEstimatedDuration();
        return estimated <= targetDurationSeconds;
    }

    // Getters and Setters

    public String getHook() {
        return hook;
    }

    public void setHook(String hook) {
        this.hook = hook;
        this.estimatedWordCount = countWords();
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
        this.estimatedWordCount = countWords();
    }

    public String getCallToAction() {
        return callToAction;
    }

    public void setCallToAction(String callToAction) {
        this.callToAction = callToAction;
        this.estimatedWordCount = countWords();
    }

    public int getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public void setTargetDurationSeconds(int targetDurationSeconds) {
        this.targetDurationSeconds = targetDurationSeconds;
    }

    public int getEstimatedWordCount() {
        return estimatedWordCount;
    }

    public List<PauseMarker> getPauseMarkers() {
        return pauseMarkers;
    }

    public void setPauseMarkers(List<PauseMarker> pauseMarkers) {
        this.pauseMarkers = pauseMarkers;
    }

    /**
     * Adds a pause marker at a specific position.
     *
     * @param position word position
     * @param duration pause duration in seconds
     */
    public void addPauseMarker(int position, double duration) {
        pauseMarkers.add(new PauseMarker(position, duration));
    }

    @Override
    public String toString() {
        return String.format(
            "VoiceoverScript{words=%d, estimated=%ds, target=%ds, valid=%s}",
            estimatedWordCount, getEstimatedDuration(), targetDurationSeconds, isValid()
        );
    }

    /**
     * Inner class for pause markers in script.
     */
    public static class PauseMarker {
        private int position;      // Word position
        private double duration;   // Pause duration in seconds

        public PauseMarker(int position, double duration) {
            this.position = position;
            this.duration = duration;
        }

        public int getPosition() {
            return position;
        }

        public double getDuration() {
            return duration;
        }
    }
}
