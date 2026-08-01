package com.videogenerator.model;

/**
 * Configuration for mixing voiceover and background music.
 * Controls volume levels, ducking, and fade effects.
 */
public class AudioMixConfig {
    private double voiceoverVolume;    // Voiceover volume (0.0 to 1.0)
    private double musicVolume;        // Background music volume (0.0 to 1.0)
    private boolean duckingEnabled;    // Enable audio ducking (lower music when voice plays)
    private double duckingAmount;      // Ducking amount in dB (-20 to 0)
    private boolean fadeInEnabled;     // Fade in at start
    private boolean fadeOutEnabled;    // Fade out at end
    private double fadeInDuration;     // Fade in duration in seconds
    private double fadeOutDuration;    // Fade out duration in seconds
    private boolean normalizeAudio;    // Normalize final audio levels

    public AudioMixConfig() {
        // Default settings optimized for Shorts
        this.voiceoverVolume = 1.0;        // Full volume for voice
        this.musicVolume = 0.25;           // Background music at 25%
        this.duckingEnabled = true;        // Enable ducking
        this.duckingAmount = -10.0;        // Lower music by 10dB during voice
        this.fadeInEnabled = true;
        this.fadeOutEnabled = true;
        this.fadeInDuration = 0.5;
        this.fadeOutDuration = 1.0;
        this.normalizeAudio = true;
    }

    /**
     * Creates a config with prominent voiceover (music very low).
     *
     * @return audio mix config
     */
    public static AudioMixConfig createVoiceProminentConfig() {
        AudioMixConfig config = new AudioMixConfig();
        config.setVoiceoverVolume(1.0);
        config.setMusicVolume(0.15);       // Very low music
        config.setDuckingEnabled(true);
        config.setDuckingAmount(-15.0);    // Aggressive ducking
        return config;
    }

    /**
     * Creates a config with balanced voice and music.
     *
     * @return audio mix config
     */
    public static AudioMixConfig createBalancedConfig() {
        AudioMixConfig config = new AudioMixConfig();
        config.setVoiceoverVolume(1.0);
        config.setMusicVolume(0.35);       // Moderate music
        config.setDuckingEnabled(true);
        config.setDuckingAmount(-8.0);     // Moderate ducking
        return config;
    }

    /**
     * Creates a config for ambient/ASMR content (no voiceover dominance).
     *
     * @return audio mix config
     */
    public static AudioMixConfig createAmbientConfig() {
        AudioMixConfig config = new AudioMixConfig();
        config.setVoiceoverVolume(0.8);
        config.setMusicVolume(0.5);        // Higher music
        config.setDuckingEnabled(false);   // No ducking
        config.setDuckingAmount(0.0);
        return config;
    }

    /**
     * Validates the configuration.
     *
     * @return true if valid
     */
    public boolean isValid() {
        if (voiceoverVolume < 0.0 || voiceoverVolume > 1.0) {
            return false;
        }
        if (musicVolume < 0.0 || musicVolume > 1.0) {
            return false;
        }
        if (duckingAmount < -30.0 || duckingAmount > 0.0) {
            return false;
        }
        if (fadeInDuration < 0.0 || fadeInDuration > 5.0) {
            return false;
        }
        if (fadeOutDuration < 0.0 || fadeOutDuration > 5.0) {
            return false;
        }
        return true;
    }

    /**
     * Gets voiceover volume as a decimal for FFmpeg.
     *
     * @return volume decimal
     */
    public String getVoiceoverVolumeString() {
        return String.format("%.2f", voiceoverVolume);
    }

    /**
     * Gets music volume as a decimal for FFmpeg.
     *
     * @return volume decimal
     */
    public String getMusicVolumeString() {
        return String.format("%.2f", musicVolume);
    }

    /**
     * Gets ducking amount as a string for FFmpeg compand filter.
     *
     * @return ducking string
     */
    public String getDuckingAmountString() {
        return String.format("%.1fdB", duckingAmount);
    }

    // Getters and Setters

    public double getVoiceoverVolume() {
        return voiceoverVolume;
    }

    public void setVoiceoverVolume(double voiceoverVolume) {
        this.voiceoverVolume = voiceoverVolume;
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = musicVolume;
    }

    public boolean isDuckingEnabled() {
        return duckingEnabled;
    }

    public void setDuckingEnabled(boolean duckingEnabled) {
        this.duckingEnabled = duckingEnabled;
    }

    public double getDuckingAmount() {
        return duckingAmount;
    }

    public void setDuckingAmount(double duckingAmount) {
        this.duckingAmount = duckingAmount;
    }

    public boolean isFadeInEnabled() {
        return fadeInEnabled;
    }

    public void setFadeInEnabled(boolean fadeInEnabled) {
        this.fadeInEnabled = fadeInEnabled;
    }

    public boolean isFadeOutEnabled() {
        return fadeOutEnabled;
    }

    public void setFadeOutEnabled(boolean fadeOutEnabled) {
        this.fadeOutEnabled = fadeOutEnabled;
    }

    public double getFadeInDuration() {
        return fadeInDuration;
    }

    public void setFadeInDuration(double fadeInDuration) {
        this.fadeInDuration = fadeInDuration;
    }

    public double getFadeOutDuration() {
        return fadeOutDuration;
    }

    public void setFadeOutDuration(double fadeOutDuration) {
        this.fadeOutDuration = fadeOutDuration;
    }

    public boolean isNormalizeAudio() {
        return normalizeAudio;
    }

    public void setNormalizeAudio(boolean normalizeAudio) {
        this.normalizeAudio = normalizeAudio;
    }

    @Override
    public String toString() {
        return String.format(
            "AudioMixConfig{voice=%.2f, music=%.2f, ducking=%s(%.1fdB), normalize=%s}",
            voiceoverVolume, musicVolume, duckingEnabled, duckingAmount, normalizeAudio
        );
    }
}
