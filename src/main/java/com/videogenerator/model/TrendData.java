package com.videogenerator.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents trending topic data from various sources (YouTube, Google Trends, etc.).
 */
public class TrendData {
    private Integer id;
    private String topic;
    private String keyword;
    private int searchVolume;        // Monthly search volume (from Google Trends)
    private double trendScore;       // 0.0 to 1.0 (normalized trend strength)
    private String source;           // "youtube", "google_trends", etc.
    private LocalDateTime date;

    public TrendData() {
        this.date = LocalDateTime.now();
    }

    public TrendData(String topic, String keyword, String source) {
        this.topic = topic;
        this.keyword = keyword;
        this.source = source;
        this.date = LocalDateTime.now();
    }

    public TrendData(String topic, String keyword, int searchVolume, double trendScore, String source) {
        this.topic = topic;
        this.keyword = keyword;
        this.searchVolume = searchVolume;
        this.trendScore = trendScore;
        this.source = source;
        this.date = LocalDateTime.now();
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getSearchVolume() {
        return searchVolume;
    }

    public void setSearchVolume(int searchVolume) {
        this.searchVolume = searchVolume;
    }

    public double getTrendScore() {
        return trendScore;
    }

    public void setTrendScore(double trendScore) {
        this.trendScore = trendScore;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * Checks if this trend is recent (within last 24 hours).
     *
     * @return true if trend is from today
     */
    public boolean isRecent() {
        return date != null && date.isAfter(LocalDateTime.now().minusDays(1));
    }

    /**
     * Checks if this trend has significant search volume.
     *
     * @param minVolume minimum monthly search volume
     * @return true if volume is above threshold
     */
    public boolean hasSignificantVolume(int minVolume) {
        return searchVolume >= minVolume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrendData trendData = (TrendData) o;
        return Objects.equals(topic, trendData.topic) &&
               Objects.equals(keyword, trendData.keyword) &&
               Objects.equals(source, trendData.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, keyword, source);
    }

    @Override
    public String toString() {
        return String.format(
            "TrendData{topic='%s', keyword='%s', volume=%d, score=%.2f, source='%s'}",
            topic, keyword, searchVolume, trendScore, source
        );
    }
}
