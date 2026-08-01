package com.videogenerator.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Represents a YouTube niche with its metrics and scoring.
 */
public class NicheData {
    private Integer id;
    private String topic;
    private List<String> keywords;
    private double competitionScore;     // 0.0 (low competition) to 1.0 (high competition)
    private double viralPotential;       // 0.0 to 1.0 (based on trend analysis)
    private double monetizationScore;    // 0.0 to 1.0 (ad revenue potential)
    private double overallScore;         // Calculated: (viralPotential × monetization) / competition
    private LocalDateTime lastUpdated;

    public NicheData() {
    }

    public NicheData(String topic, List<String> keywords) {
        this.topic = topic;
        this.keywords = keywords;
        this.lastUpdated = LocalDateTime.now();
    }

    public NicheData(String topic, List<String> keywords, double competitionScore,
                     double viralPotential, double monetizationScore) {
        this.topic = topic;
        this.keywords = keywords;
        this.competitionScore = competitionScore;
        this.viralPotential = viralPotential;
        this.monetizationScore = monetizationScore;
        this.overallScore = calculateOverallScore();
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Calculates the overall niche score.
     * Formula: (viralPotential × monetization) / competition × 10
     * Uses minimum competition of 0.01 to prevent division by zero.
     *
     * IMPORTANT: This method is immutable - does not modify instance variables.
     *
     * @return overall score between 0.0 and 10.0
     */
    public double calculateOverallScore() {
        // Use local variable to prevent side-effects (thread-safe + immutable)
        double safeCompetition = Math.max(competitionScore, 0.01);
        return (viralPotential * monetizationScore) / safeCompetition * 10.0;
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public double getCompetitionScore() {
        return competitionScore;
    }

    public void setCompetitionScore(double competitionScore) {
        this.competitionScore = competitionScore;
        this.overallScore = calculateOverallScore();
    }

    public double getViralPotential() {
        return viralPotential;
    }

    public void setViralPotential(double viralPotential) {
        this.viralPotential = viralPotential;
        this.overallScore = calculateOverallScore();
    }

    public double getMonetizationScore() {
        return monetizationScore;
    }

    public void setMonetizationScore(double monetizationScore) {
        this.monetizationScore = monetizationScore;
        this.overallScore = calculateOverallScore();
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Gets a formatted string of all keywords.
     *
     * @return comma-separated keywords
     */
    public String getKeywordsAsString() {
        return keywords != null ? String.join(", ", keywords) : "";
    }

    /**
     * Checks if this niche meets minimum quality threshold.
     *
     * @param minScore minimum overall score required
     * @return true if niche score is above threshold
     */
    public boolean meetsThreshold(double minScore) {
        return overallScore >= minScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NicheData nicheData = (NicheData) o;
        return Objects.equals(topic, nicheData.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic);
    }

    @Override
    public String toString() {
        return String.format(
            "NicheData{topic='%s', score=%.2f, viral=%.2f, competition=%.2f, monetization=%.2f, keywords=%d}",
            topic, overallScore, viralPotential, competitionScore, monetizationScore,
            keywords != null ? keywords.size() : 0
        );
    }
}
