package com.videogenerator.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Character-level timing returned by ElevenLabs /with-timestamps.
 * This is the single source of truth for BOTH scene cut points and
 * subtitle timing, which makes audio/visual desync structurally impossible.
 */
public class Alignment {
    @SerializedName("characters")
    private List<String> characters;

    @SerializedName("character_start_times_seconds")
    private List<Double> characterStartTimesSeconds;

    @SerializedName("character_end_times_seconds")
    private List<Double> characterEndTimesSeconds;

    public int length() {
        return characters == null ? 0 : characters.size();
    }

    public double endOf(int charIndex) {
        return characterEndTimesSeconds.get(charIndex);
    }

    public double totalDuration() {
        return length() == 0 ? 0.0 : characterEndTimesSeconds.get(length() - 1);
    }

    public List<String> getCharacters() {
        return characters;
    }

    public void setCharacters(List<String> characters) {
        this.characters = characters;
    }

    public List<Double> getCharacterStartTimesSeconds() {
        return characterStartTimesSeconds;
    }

    public void setCharacterStartTimesSeconds(List<Double> characterStartTimesSeconds) {
        this.characterStartTimesSeconds = characterStartTimesSeconds;
    }

    public List<Double> getCharacterEndTimesSeconds() {
        return characterEndTimesSeconds;
    }

    public void setCharacterEndTimesSeconds(List<Double> characterEndTimesSeconds) {
        this.characterEndTimesSeconds = characterEndTimesSeconds;
    }
}
