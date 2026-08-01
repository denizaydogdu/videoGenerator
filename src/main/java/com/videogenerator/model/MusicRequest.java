package com.videogenerator.model;

/**
 * Request model for Suno API music generation
 */
public class MusicRequest {
    private String gptDescriptionPrompt;
    private boolean makeInstrumental;
    private String model;
    private int waitAudio;

    public MusicRequest() {
        this.makeInstrumental = true;
        this.model = "chirp-v3-5";
        this.waitAudio = 0; // Will use polling instead
    }

    public MusicRequest(String prompt) {
        this();
        this.gptDescriptionPrompt = prompt;
    }

    public String getGptDescriptionPrompt() {
        return gptDescriptionPrompt;
    }

    public void setGptDescriptionPrompt(String gptDescriptionPrompt) {
        this.gptDescriptionPrompt = gptDescriptionPrompt;
    }

    public boolean isMakeInstrumental() {
        return makeInstrumental;
    }

    public void setMakeInstrumental(boolean makeInstrumental) {
        this.makeInstrumental = makeInstrumental;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getWaitAudio() {
        return waitAudio;
    }

    public void setWaitAudio(int waitAudio) {
        this.waitAudio = waitAudio;
    }
}
