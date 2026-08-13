package com.videogenerator.model;

/**
 * Configuration for Text-to-Speech voice generation.
 * Contains settings for ElevenLabs or other TTS providers.
 */
public class VoiceConfig {
    private String voiceId;           // Voice ID from TTS provider
    private String model;             // TTS model (e.g., "eleven_multilingual_v2")
    private double stability;         // Voice stability (0.0 to 1.0)
    private double similarityBoost;   // Similarity boost (0.0 to 1.0)
    private double style;             // Style exaggeration (0.0 to 1.0)
    private boolean useSpeakerBoost;  // Enable speaker boost
    private String language;          // Language code (e.g., "en", "tr")

    // ElevenLabs default voices
    public static final String VOICE_RACHEL = "21m00Tcm4TlvDq8ikWAM";  // Female, American
    public static final String VOICE_ADAM = "pNInz6obpgDQGcFmaJgB";    // Male, American
    public static final String VOICE_ANTONI = "ErXwobaYiN019PkySvjV";  // Male, Well-rounded
    public static final String VOICE_BELLA = "EXAVITQu4vr4xnSDxMaL";   // Female, Soft
    public static final String VOICE_DOMI = "AZnzlk1XvdvUeBnXmlld";    // Female, Strong
    public static final String VOICE_GEORGE = "JBFqnCBsd6RMkjVDRZzb";  // Male, British

    public VoiceConfig() {
        // Default settings for natural-sounding voice
        this.voiceId = VOICE_RACHEL;
        this.model = "eleven_multilingual_v2";
        this.stability = 0.5;
        this.similarityBoost = 0.75;
        this.style = 0.0;
        this.useSpeakerBoost = true;
        this.language = "en";
    }

    public VoiceConfig(String voiceId) {
        this();
        this.voiceId = voiceId;
    }

    /**
     * Creates a config optimized for energetic/exciting content.
     *
     * @return voice config
     */
    public static VoiceConfig createEnergeticConfig() {
        VoiceConfig config = new VoiceConfig();
        config.setStability(0.4);           // Less stable = more expressive
        config.setSimilarityBoost(0.8);
        config.setStyle(0.3);               // Some style exaggeration
        config.setUseSpeakerBoost(true);
        return config;
    }

    /**
     * Creates a config optimized for calm/relaxing content.
     *
     * @return voice config
     */
    public static VoiceConfig createCalmConfig() {
        VoiceConfig config = new VoiceConfig();
        config.setStability(0.7);           // More stable = more consistent
        config.setSimilarityBoost(0.7);
        config.setStyle(0.0);               // No style exaggeration
        config.setUseSpeakerBoost(false);
        return config;
    }

    /**
     * Creates a config optimized for storytelling.
     *
     * @return voice config
     */
    public static VoiceConfig createStorytellingConfig() {
        VoiceConfig config = new VoiceConfig();
        config.setStability(0.6);
        config.setSimilarityBoost(0.75);
        config.setStyle(0.2);
        config.setUseSpeakerBoost(true);
        return config;
    }

    /**
     * Validates the configuration.
     *
     * @return true if valid
     */
    public boolean isValid() {
        if (voiceId == null || voiceId.isEmpty()) {
            return false;
        }
        if (stability < 0.0 || stability > 1.0) {
            return false;
        }
        if (similarityBoost < 0.0 || similarityBoost > 1.0) {
            return false;
        }
        if (style < 0.0 || style > 1.0) {
            return false;
        }
        return true;
    }

    // Getters and Setters

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getStability() {
        return stability;
    }

    public void setStability(double stability) {
        this.stability = stability;
    }

    public double getSimilarityBoost() {
        return similarityBoost;
    }

    public void setSimilarityBoost(double similarityBoost) {
        this.similarityBoost = similarityBoost;
    }

    public double getStyle() {
        return style;
    }

    public void setStyle(double style) {
        this.style = style;
    }

    public boolean isUseSpeakerBoost() {
        return useSpeakerBoost;
    }

    public void setUseSpeakerBoost(boolean useSpeakerBoost) {
        this.useSpeakerBoost = useSpeakerBoost;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return String.format(
            "VoiceConfig{voiceId='%s', model='%s', stability=%.2f, similarity=%.2f, style=%.2f}",
            voiceId, model, stability, similarityBoost, style
        );
    }
}
