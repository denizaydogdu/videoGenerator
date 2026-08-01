package com.videogenerator.service;

import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import com.videogenerator.api.KeywordApiClient;
import com.videogenerator.api.YouTubeApiClient;
import com.videogenerator.model.TrendData;
import com.videogenerator.repository.TrendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes trends from YouTube and Google Trends to identify popular topics.
 * Extracts patterns, keywords, and engagement metrics from trending content.
 */
public class TrendAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(TrendAnalyzer.class);
    private final YouTubeApiClient youtubeClient;
    private final KeywordApiClient keywordClient;
    private final TrendRepository trendRepository;

    public TrendAnalyzer(YouTubeApiClient youtubeClient, KeywordApiClient keywordClient) {
        this.youtubeClient = youtubeClient;
        this.keywordClient = keywordClient;
        this.trendRepository = new TrendRepository();
    }

    /**
     * Analyzes trending Shorts in a specific region.
     *
     * @param regionCode region code (e.g., "US", "TR", "GB")
     * @param limit max number of shorts to analyze
     * @return list of trending topics with scores
     */
    public List<TrendData> analyzeTrendingShorts(String regionCode, int limit) {
        logger.info("Analyzing trending Shorts in region: {}", regionCode);

        List<TrendData> trends = new ArrayList<>();

        try {
            // Get trending Shorts from YouTube
            List<Video> trendingShorts = youtubeClient.getTrendingShortsFiltered(regionCode, limit);

            if (trendingShorts.isEmpty()) {
                logger.warn("No trending Shorts found for region: {}", regionCode);
                return trends;
            }

            // Extract topics and keywords from titles
            Map<String, TopicMetrics> topicMetrics = new HashMap<>();

            for (Video video : trendingShorts) {
                String title = video.getSnippet().getTitle();
                String description = video.getSnippet().getDescription();
                long views = video.getStatistics().getViewCount().longValue();
                long likes = video.getStatistics().getLikeCount() != null ?
                        video.getStatistics().getLikeCount().longValue() : 0;

                // Extract keywords from title
                List<String> keywords = extractKeywords(title + " " + description);

                for (String keyword : keywords) {
                    topicMetrics.putIfAbsent(keyword, new TopicMetrics());
                    TopicMetrics metrics = topicMetrics.get(keyword);
                    metrics.occurrences++;
                    metrics.totalViews += views;
                    metrics.totalLikes += likes;
                }
            }

            // Convert to TrendData and calculate scores
            for (Map.Entry<String, TopicMetrics> entry : topicMetrics.entrySet()) {
                String topic = entry.getKey();
                TopicMetrics metrics = entry.getValue();

                // Calculate trend score based on occurrences and engagement
                double trendScore = calculateTrendScore(metrics, trendingShorts.size());

                // Only include topics that appear in at least 2 videos
                if (metrics.occurrences >= 2 && trendScore > 0.3) {
                    TrendData trend = new TrendData();
                    trend.setTopic(topic);
                    trend.setKeyword(topic);
                    trend.setSearchVolume((int) (metrics.totalViews / metrics.occurrences));
                    trend.setTrendScore(trendScore);
                    trend.setSource("youtube_trending");
                    trend.setDate(LocalDateTime.now());

                    trends.add(trend);

                    // Save to database
                    try {
                        trendRepository.save(trend);
                    } catch (Exception e) {
                        logger.warn("Failed to save trend: {}", topic, e);
                    }
                }
            }

            // Sort by trend score (descending)
            trends.sort((a, b) -> Double.compare(b.getTrendScore(), a.getTrendScore()));

            logger.info("Identified {} trending topics from {} Shorts",
                    trends.size(), trendingShorts.size());

        } catch (IOException e) {
            logger.error("Failed to analyze trending Shorts", e);
        }

        return trends;
    }

    /**
     * Analyzes search trends for a specific keyword.
     *
     * @param keyword the keyword to analyze
     * @param regionCode region code
     * @return trend data for the keyword
     */
    public TrendData analyzeKeywordTrend(String keyword, String regionCode) {
        logger.info("Analyzing keyword trend: {} in region: {}", keyword, regionCode);

        try {
            // Get trend data from Google Trends
            KeywordApiClient.KeywordTrendData keywordData =
                    keywordClient.getKeywordTrend(keyword, regionCode);

            // Get YouTube search results for additional validation
            SearchListResponse searchResponse = youtubeClient.searchShorts(keyword, 10);
            List<SearchResult> searchResults = searchResponse.getItems();

            // Calculate combined trend score
            double youtubeTrendScore = calculateYouTubeTrendScore(searchResults);
            double combinedScore = (keywordData.getTrendScore() + youtubeTrendScore) / 2.0;

            TrendData trend = new TrendData();
            trend.setTopic(keyword);
            trend.setKeyword(keyword);
            trend.setSearchVolume(keywordData.getSearchVolume());
            trend.setTrendScore(combinedScore);
            trend.setSource("combined");
            trend.setDate(LocalDateTime.now());

            // Save to database
            try {
                trendRepository.save(trend);
            } catch (Exception e) {
                logger.warn("Failed to save keyword trend", e);
            }

            return trend;

        } catch (IOException e) {
            logger.error("Failed to analyze keyword trend", e);
            return null;
        }
    }

    /**
     * Gets recent trends from the database.
     *
     * @param hours hours to look back
     * @param minScore minimum trend score
     * @return list of recent trends
     */
    public List<TrendData> getRecentTrends(int hours, double minScore) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<TrendData> allTrends = trendRepository.findAfterDate(since);

        return allTrends.stream()
                .filter(trend -> trend.getTrendScore() >= minScore)
                .sorted((a, b) -> Double.compare(b.getTrendScore(), a.getTrendScore()))
                .collect(Collectors.toList());
    }

    /**
     * Identifies emerging trends (rapidly growing keywords).
     *
     * @param regionCode region to analyze
     * @return list of emerging trend topics
     */
    public List<String> identifyEmergingTrends(String regionCode) {
        logger.info("Identifying emerging trends in region: {}", regionCode);

        List<String> emergingTrends = new ArrayList<>();

        try {
            // Get current trending Shorts
            List<Video> currentShorts = youtubeClient.getTrendingShortsFiltered(regionCode, 20);

            // Get historical trends from database (last 7 days)
            LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
            List<TrendData> historicalTrends = trendRepository.findAfterDate(weekAgo);
            Set<String> historicalTopics = historicalTrends.stream()
                    .map(TrendData::getTopic)
                    .collect(Collectors.toSet());

            // Find topics that are trending now but weren't in the past week
            for (Video video : currentShorts) {
                List<String> keywords = extractKeywords(video.getSnippet().getTitle());

                for (String keyword : keywords) {
                    if (!historicalTopics.contains(keyword) &&
                        keyword.length() > 3 &&
                        !emergingTrends.contains(keyword)) {

                        emergingTrends.add(keyword);
                    }
                }
            }

            logger.info("Identified {} emerging trends", emergingTrends.size());

        } catch (IOException e) {
            logger.error("Failed to identify emerging trends", e);
        }

        return emergingTrends;
    }

    /**
     * Extracts meaningful keywords from text.
     * Removes stop words and very short words.
     *
     * @param text the text to analyze
     * @return list of keywords
     */
    private List<String> extractKeywords(String text) {
        // Common stop words to filter out
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                "of", "with", "by", "from", "up", "about", "into", "through", "during",
                "is", "are", "was", "were", "been", "be", "have", "has", "had", "do",
                "does", "did", "will", "would", "could", "should", "may", "might",
                "this", "that", "these", "those", "i", "you", "he", "she", "it", "we",
                "they", "my", "your", "his", "her", "its", "our", "their", "what",
                "which", "who", "when", "where", "why", "how", "all", "each", "every",
                "both", "few", "more", "most", "other", "some", "such", "no", "not",
                "only", "own", "same", "so", "than", "too", "very", "can", "just",
                "shorts", "short", "video"
        ));

        // Split into words, lowercase, remove punctuation
        String[] words = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+");

        List<String> keywords = new ArrayList<>();

        for (String word : words) {
            // Keep words that are:
            // - Not stop words
            // - At least 4 characters
            // - Alphabetic (not numbers)
            if (!stopWords.contains(word) &&
                word.length() >= 4 &&
                word.matches(".*[a-z].*")) {

                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Calculates trend score based on metrics.
     *
     * @param metrics topic metrics
     * @param totalVideos total number of videos analyzed
     * @return trend score (0.0 to 1.0)
     */
    private double calculateTrendScore(TopicMetrics metrics, int totalVideos) {
        // Frequency score: how often the topic appears
        double frequencyScore = (double) metrics.occurrences / totalVideos;

        // Engagement score: average views and likes
        double avgViews = (double) metrics.totalViews / metrics.occurrences;
        double avgLikes = (double) metrics.totalLikes / metrics.occurrences;

        // Normalize engagement (assuming 1M views is max for score calculation)
        double viewScore = Math.min(avgViews / 1_000_000.0, 1.0);
        double likeScore = Math.min(avgLikes / 50_000.0, 1.0);
        double engagementScore = (viewScore + likeScore) / 2.0;

        // Combined score (weighted)
        double trendScore = (frequencyScore * 0.4) + (engagementScore * 0.6);

        return Math.min(trendScore, 1.0);
    }

    /**
     * Calculates trend score from YouTube search results.
     *
     * @param searchResults YouTube search results
     * @return trend score (0.0 to 1.0)
     */
    private double calculateYouTubeTrendScore(List<SearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return 0.0;
        }

        // If there are many results, it's trending
        double resultCountScore = Math.min(searchResults.size() / 50.0, 1.0);

        // Additional analysis could be done here (view counts, recency, etc.)
        // For now, we'll use result count as the primary indicator

        return resultCountScore;
    }

    /**
     * Cleans up old trend data from the database.
     *
     * @param daysOld number of days to keep
     * @return number of deleted trends
     */
    public int cleanupOldTrends(int daysOld) {
        logger.info("Cleaning up trends older than {} days", daysOld);
        return trendRepository.deleteOlderThan(daysOld);
    }

    /**
     * Helper class to track topic metrics.
     */
    private static class TopicMetrics {
        int occurrences = 0;
        long totalViews = 0;
        long totalLikes = 0;
    }
}
