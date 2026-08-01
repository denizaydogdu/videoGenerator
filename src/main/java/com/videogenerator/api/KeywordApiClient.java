package com.videogenerator.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for keyword research and trend analysis.
 * Uses Google Trends unofficial API for keyword volume estimation.
 *
 * IMPORTANT: This class implements AutoCloseable. Always use try-with-resources:
 * try (KeywordApiClient client = new KeywordApiClient()) {
 *     client.getKeywordTrend(...);
 * }
 *
 * Note: This is a simplified implementation. For production, consider using:
 * - SerpAPI (https://serpapi.com)
 * - DataForSEO (https://dataforseo.com)
 * - SEMrush API
 */
public class KeywordApiClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KeywordApiClient.class);
    private static final String GOOGLE_TRENDS_BASE = "https://trends.google.com/trends/api";
    private final Gson gson;
    private final CloseableHttpClient httpClient;

    public KeywordApiClient() {
        this.gson = new Gson();
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Gets trend data for a keyword.
     * This is a simplified implementation that estimates trend strength.
     *
     * @param keyword the keyword to analyze
     * @param geo geographic region code (e.g., "US", "TR", "GB")
     * @return keyword trend data
     */
    public KeywordTrendData getKeywordTrend(String keyword, String geo) {
        logger.info("Analyzing keyword trend: {} in region: {}", keyword, geo);

        try {
            // Note: Google Trends API is unofficial and may be rate-limited
            // For production, use a paid service like SerpAPI or DataForSEO

            // Build request URL
            String encodedKeyword = encodeUrl(keyword);
            String url = String.format(
                    "%s/explore?hl=en-US&tz=-120&req={\"comparisonItem\":[{\"keyword\":\"%s\",\"geo\":\"%s\",\"time\":\"today 12-m\"}]}",
                    GOOGLE_TRENDS_BASE, encodedKeyword, geo
            );

            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "Mozilla/5.0 (compatible; YouTubeShortsGenerator/1.0)");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();

                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    return parseGoogleTrendsResponse(keyword, geo, responseBody);

                } else if (statusCode == 429) {
                    logger.warn("Rate limited by Google Trends, using fallback estimation");
                    return estimateKeywordTrend(keyword, geo);

                } else {
                    logger.warn("Google Trends API returned status {}, using fallback", statusCode);
                    return estimateKeywordTrend(keyword, geo);
                }
            }

        } catch (IOException | org.apache.hc.core5.http.ParseException e) {
            logger.warn("Failed to fetch Google Trends data: {}, using estimation", e.getMessage());
            return estimateKeywordTrend(keyword, geo);
        }
    }

    /**
     * Gets trend data for multiple keywords.
     *
     * @param keywords list of keywords
     * @param geo geographic region
     * @return map of keyword to trend data
     */
    public Map<String, KeywordTrendData> getBatchKeywordTrends(List<String> keywords, String geo) {
        Map<String, KeywordTrendData> results = new HashMap<>();

        for (String keyword : keywords) {
            try {
                KeywordTrendData data = getKeywordTrend(keyword, geo);
                results.put(keyword, data);

                // Rate limiting: sleep between requests
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                logger.warn("Batch processing interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Processed {} keywords", results.size());
        return results;
    }

    /**
     * Estimates keyword trend based on heuristics when API is unavailable.
     * This is a fallback method that provides reasonable estimates.
     *
     * @param keyword the keyword
     * @param geo region code
     * @return estimated trend data
     */
    private KeywordTrendData estimateKeywordTrend(String keyword, String geo) {
        logger.debug("Using estimation for keyword: {}", keyword);

        // Simple heuristic: estimate based on keyword characteristics
        int estimatedVolume = estimateSearchVolume(keyword);
        double trendScore = estimateTrendScore(keyword);
        int competition = estimateCompetition(keyword);

        return new KeywordTrendData(keyword, geo, estimatedVolume, trendScore, competition);
    }

    /**
     * Estimates monthly search volume based on keyword characteristics.
     */
    private int estimateSearchVolume(String keyword) {
        // Base volume
        int volume = 10000;

        // Adjust based on keyword length (shorter = more popular)
        if (keyword.length() < 10) {
            volume += 5000;
        } else if (keyword.length() > 30) {
            volume -= 3000;
        }

        // Adjust for word count (1-2 words = more popular)
        int wordCount = keyword.split("\\s+").length;
        if (wordCount <= 2) {
            volume += 3000;
        } else if (wordCount > 4) {
            volume -= 2000;
        }

        // Common high-volume keywords
        String lowerKeyword = keyword.toLowerCase();
        if (lowerKeyword.contains("how to") || lowerKeyword.contains("tutorial")) {
            volume += 5000;
        }
        if (lowerKeyword.contains("best") || lowerKeyword.contains("top")) {
            volume += 4000;
        }
        if (lowerKeyword.contains("shorts") || lowerKeyword.contains("viral")) {
            volume += 3000;
        }

        // Ensure volume is positive and reasonable
        volume = Math.max(volume, 1000);
        volume = Math.min(volume, 100000);

        // Add some randomness for variation
        volume += (int) (Math.random() * 2000 - 1000);

        return volume;
    }

    /**
     * Estimates trend score (0.0 to 1.0) based on keyword characteristics.
     */
    private double estimateTrendScore(String keyword) {
        double score = 0.5; // Base score

        String lowerKeyword = keyword.toLowerCase();

        // Trending topics
        if (lowerKeyword.contains("2025") || lowerKeyword.contains("2024")) {
            score += 0.2;
        }
        if (lowerKeyword.contains("new") || lowerKeyword.contains("latest")) {
            score += 0.15;
        }
        if (lowerKeyword.contains("viral") || lowerKeyword.contains("trending")) {
            score += 0.1;
        }

        // Evergreen topics (slightly lower trend but stable)
        if (lowerKeyword.contains("how to") || lowerKeyword.contains("tutorial")) {
            score += 0.05;
        }

        // Ensure score is between 0.0 and 1.0
        score = Math.max(0.0, Math.min(1.0, score));

        return score;
    }

    /**
     * Estimates competition level (0-100, lower = less competitive).
     */
    private int estimateCompetition(String keyword) {
        int competition = 50; // Base competition

        // Long-tail keywords (more specific) have less competition
        int wordCount = keyword.split("\\s+").length;
        if (wordCount >= 4) {
            competition -= 20;
        } else if (wordCount <= 2) {
            competition += 15;
        }

        // Broad keywords are more competitive
        String lowerKeyword = keyword.toLowerCase();
        if (lowerKeyword.contains("music") || lowerKeyword.contains("video") ||
            lowerKeyword.contains("shorts") || lowerKeyword.contains("youtube")) {
            competition += 20;
        }

        // Niche keywords are less competitive
        if (lowerKeyword.length() > 30) {
            competition -= 15;
        }

        // Ensure competition is between 0 and 100
        competition = Math.max(0, Math.min(100, competition));

        return competition;
    }

    /**
     * Parses Google Trends API response.
     * This is a simplified parser for the unofficial API.
     */
    private KeywordTrendData parseGoogleTrendsResponse(String keyword, String geo, String responseBody) {
        try {
            // Google Trends returns JSONP with ")]}'," prefix
            if (responseBody.startsWith(")]}',")) {
                responseBody = responseBody.substring(5);
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Extract interest over time (simplified)
            int estimatedVolume = 10000; // Default
            double trendScore = 0.5;

            if (json.has("default") && json.getAsJsonObject("default").has("timelineData")) {
                JsonArray timeline = json.getAsJsonObject("default")
                        .getAsJsonArray("timelineData");

                if (timeline.size() > 0) {
                    // Get average interest
                    double totalInterest = 0;
                    int count = 0;

                    for (JsonElement element : timeline) {
                        JsonObject point = element.getAsJsonObject();
                        if (point.has("value")) {
                            JsonArray values = point.getAsJsonArray("value");
                            if (values.size() > 0) {
                                totalInterest += values.get(0).getAsInt();
                                count++;
                            }
                        }
                    }

                    if (count > 0) {
                        double avgInterest = totalInterest / count;
                        // Normalize to 0-1 scale (Google Trends uses 0-100)
                        trendScore = avgInterest / 100.0;
                        // Estimate volume based on interest
                        estimatedVolume = (int) (avgInterest * 1000);
                    }
                }
            }

            int competition = estimateCompetition(keyword);
            return new KeywordTrendData(keyword, geo, estimatedVolume, trendScore, competition);

        } catch (Exception e) {
            logger.warn("Failed to parse Google Trends response, using estimation", e);
            return estimateKeywordTrend(keyword, geo);
        }
    }

    /**
     * URL encodes a string.
     */
    private String encodeUrl(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            logger.error("Failed to encode URL", e);
            return value;
        }
    }

    /**
     * Closes the HTTP client.
     * Called automatically when used with try-with-resources.
     */
    @Override
    public void close() {
        try {
            httpClient.close();
            logger.debug("KeywordApiClient HTTP client closed successfully");
        } catch (IOException e) {
            logger.error("Failed to close HTTP client", e);
        }
    }

    /**
     * Data class for keyword trend information.
     */
    public static class KeywordTrendData {
        private final String keyword;
        private final String geo;
        private final int searchVolume;
        private final double trendScore;
        private final int competition;

        public KeywordTrendData(String keyword, String geo, int searchVolume,
                               double trendScore, int competition) {
            this.keyword = keyword;
            this.geo = geo;
            this.searchVolume = searchVolume;
            this.trendScore = trendScore;
            this.competition = competition;
        }

        public String getKeyword() {
            return keyword;
        }

        public String getGeo() {
            return geo;
        }

        public int getSearchVolume() {
            return searchVolume;
        }

        public double getTrendScore() {
            return trendScore;
        }

        public int getCompetition() {
            return competition;
        }

        @Override
        public String toString() {
            return String.format(
                "KeywordTrendData{keyword='%s', volume=%d, trend=%.2f, competition=%d}",
                keyword, searchVolume, trendScore, competition
            );
        }
    }
}
