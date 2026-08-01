package com.videogenerator.model;

/**
 * One subtitle cue: a word group shown between start and end (seconds).
 */
public class SubtitleCue {
    private double start;
    private double end;
    private String text;

    public double getStart() {
        return start;
    }

    public void setStart(double start) {
        this.start = start;
    }

    public double getEnd() {
        return end;
    }

    public void setEnd(double end) {
        this.end = end;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
