package com.videogenerator.model;

/**
 * Represents different API providers used in the application
 */
public enum ApiProvider {
    SUNO("Suno API", "Music Generation"),
    OPENAI_SORA("OpenAI Sora", "Video Generation"),
    OPENAI_GPT("OpenAI GPT-4", "Text/Metadata Generation"),
    YOUTUBE("YouTube Data API", "Video Upload"),
    ELEVENLABS("ElevenLabs", "Text-to-Speech");

    private final String displayName;
    private final String purpose;

    ApiProvider(String displayName, String purpose) {
        this.displayName = displayName;
        this.purpose = purpose;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPurpose() {
        return purpose;
    }
}
