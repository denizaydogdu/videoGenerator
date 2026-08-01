package com.videogenerator.service;

import com.google.api.services.youtube.model.Video;
import com.videogenerator.api.KeywordApiClient;
import com.videogenerator.api.YouTubeApiClient;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.NicheData;
import com.videogenerator.model.TrendData;
import com.videogenerator.repository.NicheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for discovering and scoring YouTube Shorts niches.
 * Identifies niches with low competition, high viral potential, and strong monetization.
 */
public class NicheFinder {
    private static final Logger logger = LoggerFactory.getLogger(NicheFinder.class);
    private final Configuration config;
    private final YouTubeApiClient youtubeClient;
    private final KeywordApiClient keywordClient;
    private final TrendAnalyzer trendAnalyzer;
    private final NicheRepository nicheRepository;

    // Predefined niche categories to explore (faceless-friendly)
    private static final List<String> FACELESS_NICHE_SEEDS = Arrays.asList(
            "motivation quotes", "sleep sounds", "nature sounds", "ambient music",
            "lo-fi beats", "study music", "meditation music", "asmr",
            "facts", "did you know", "mystery stories", "true crime stories",
            "history facts", "science facts", "space facts", "animal facts",
            "life hacks", "productivity tips", "money tips", "tech tips",
            "cooking recipes", "quick recipes", "diy crafts", "art tutorials",
            "stock market", "crypto", "investing", "passive income",
            "minecraft", "gaming", "esports", "game tips",
            "funny memes", "wholesome", "satisfying", "oddly satisfying",
            "luxury lifestyle", "billionaire", "success stories", "mindset"
    );

    public NicheFinder(YouTubeApiClient youtubeClient, KeywordApiClient keywordClient,
                      TrendAnalyzer trendAnalyzer) {
        this.config = Configuration.getInstance();
        this.youtubeClient = youtubeClient;
        this.keywordClient = keywordClient;
        this.trendAnalyzer = trendAnalyzer;
        this.nicheRepository = new NicheRepository();
    }

    /**
     * Finds top niches based on trend analysis and scoring.
     *
     * @param count number of niches to return
     * @param regionCode region code for trend analysis
     * @return list of top niches
     */
    public List<NicheData> findTopNiches(int count, String regionCode) {
        logger.info("Finding top {} niches for region: {}", count, regionCode);

        List<NicheData> discoveredNiches = new ArrayList<>();

        try {
            // 1. Analyze current trending topics (includes trending shorts data)
            List<TrendData> trendingTopics = trendAnalyzer.analyzeTrendingShorts(regionCode, 30);

            // 2. Get trending shorts ONCE and reuse for all niches (OPTIMIZATION!)
            List<Video> cachedTrendingShorts = youtubeClient.getTrendingShortsFiltered(regionCode, 50);
            logger.info("Cached {} trending Shorts for niche analysis", cachedTrendingShorts.size());

            // 3. Score predefined niche seeds using cached data
            for (String nicheSeed : FACELESS_NICHE_SEEDS) {
                try {
                    NicheData niche = analyzeNicheWithCache(nicheSeed, regionCode,
                            trendingTopics, cachedTrendingShorts);

                    if (niche != null && niche.meetsThreshold(config.getNicheMinScore())) {
                        discoveredNiches.add(niche);

                        // Save to database
                        try {
                            nicheRepository.save(niche);
                        } catch (SQLException e) {
                            logger.warn("Failed to save niche: {}", nicheSeed, e);
                        }
                    }

                    // Reduced sleep (no API call needed now)
                    Thread.sleep(100);

                } catch (InterruptedException e) {
                    logger.warn("Niche discovery interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("Failed to analyze niche: {}", nicheSeed, e);
                }
            }

            // 4. Also check emerging trends using cached data
            List<String> emergingTrends = trendAnalyzer.identifyEmergingTrends(regionCode);
            for (String emergingTopic : emergingTrends) {
                try {
                    NicheData niche = analyzeNicheWithCache(emergingTopic, regionCode,
                            trendingTopics, cachedTrendingShorts);

                if (niche != null && niche.meetsThreshold(config.getNicheMinScore())) {
                    // Check if not duplicate
                    boolean isDuplicate = discoveredNiches.stream()
                            .anyMatch(n -> n.getTopic().equalsIgnoreCase(emergingTopic));

                    if (!isDuplicate) {
                        discoveredNiches.add(niche);

                        try {
                            nicheRepository.save(niche);
                        } catch (SQLException e) {
                            logger.warn("Failed to save emerging niche", e);
                        }
                    }
                }

                Thread.sleep(500);

            } catch (Exception e) {
                logger.warn("Failed to analyze emerging niche: {}", emergingTopic, e);
                }
            }

        } catch (IOException e) {
            logger.error("Failed to fetch trending data for niche discovery", e);
            // Return whatever we discovered so far
        }

        // 4. Sort by overall score and return top N
        List<NicheData> topNiches = discoveredNiches.stream()
                .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
                .limit(count)
                .collect(Collectors.toList());

        logger.info("Discovered {} total niches, returning top {}", discoveredNiches.size(), count);

        // Log top niches
        for (int i = 0; i < topNiches.size(); i++) {
            NicheData niche = topNiches.get(i);
            logger.info("{}. {} (score: {:.2f}, viral: {:.2f}, competition: {:.2f})",
                    i + 1, niche.getTopic(), niche.getOverallScore(),
                    niche.getViralPotential(), niche.getCompetitionScore());
        }

        return topNiches;
    }

    /**
     * Analyzes a specific niche using cached trending data (OPTIMIZED).
     *
     * @param nicheKeyword the niche keyword
     * @param regionCode region code
     * @param trendingTopics current trending topics for context
     * @param cachedTrendingShorts pre-fetched trending shorts (avoids duplicate API calls)
     * @return analyzed niche data
     */
    private NicheData analyzeNicheWithCache(String nicheKeyword, String regionCode,
                                           List<TrendData> trendingTopics,
                                           List<Video> cachedTrendingShorts) {
        logger.debug("Analyzing niche with cache: {}", nicheKeyword);

        // 1. Get keyword trend data
        KeywordApiClient.KeywordTrendData keywordData =
                keywordClient.getKeywordTrend(nicheKeyword, regionCode);

        // 2. Analyze YouTube competition using CACHED data (no API call!)
        double competitionScore = calculateCompetitionScore(nicheKeyword, cachedTrendingShorts);

        // 3. Calculate viral potential
        double viralPotential = calculateViralPotential(nicheKeyword, keywordData, trendingTopics);

        // 4. Calculate monetization potential
        double monetizationScore = calculateMonetizationScore(nicheKeyword, keywordData);

        // 5. Extract related keywords
        List<String> keywords = extractRelatedKeywords(nicheKeyword);

        // 6. Create NicheData
        NicheData niche = new NicheData(
                nicheKeyword,
                keywords,
                competitionScore,
                viralPotential,
                monetizationScore
        );

        return niche;
    }

    /**
     * Calculates competition score (0.0 = low competition, 1.0 = high competition).
     * Lower score is better.
     *
     * @param nicheKeyword the niche keyword
     * @param competitorVideos trending videos to analyze
     * @return competition score
     */
    private double calculateCompetitionScore(String nicheKeyword, List<Video> competitorVideos) {
        int matchCount = 0;
        long totalViews = 0;

        String lowerNiche = nicheKeyword.toLowerCase();

        for (Video video : competitorVideos) {
            String title = video.getSnippet().getTitle().toLowerCase();
            String description = video.getSnippet().getDescription().toLowerCase();

            // Check if video is in this niche
            if (title.contains(lowerNiche) || description.contains(lowerNiche)) {
                matchCount++;
                totalViews += video.getStatistics().getViewCount().longValue();
            }
        }

        // Calculate competition factors
        double frequencyFactor = (double) matchCount / competitorVideos.size();
        double avgViewsIfPresent = matchCount > 0 ? (double) totalViews / matchCount : 0;

        // High views in niche = high competition
        // Many videos in niche = high competition
        double viewCompetition = Math.min(avgViewsIfPresent / 5_000_000.0, 1.0);
        double frequencyCompetition = frequencyFactor * 2.0; // Amplify frequency impact

        double competitionScore = (viewCompetition * 0.6) + (frequencyCompetition * 0.4);

        return Math.min(competitionScore, 1.0);
    }

    /**
     * Calculates viral potential (0.0 to 1.0).
     * Higher score is better.
     *
     * @param nicheKeyword the niche keyword
     * @param keywordData keyword trend data
     * @param trendingTopics current trending topics
     * @return viral potential score
     */
    private double calculateViralPotential(String nicheKeyword,
                                          KeywordApiClient.KeywordTrendData keywordData,
                                          List<TrendData> trendingTopics) {
        // Base score from keyword trends
        double trendScore = keywordData.getTrendScore();

        // Boost if keyword appears in current trending topics
        boolean isCurrentlyTrending = trendingTopics.stream()
                .anyMatch(t -> t.getTopic().toLowerCase().contains(nicheKeyword.toLowerCase()) ||
                              nicheKeyword.toLowerCase().contains(t.getTopic().toLowerCase()));

        if (isCurrentlyTrending) {
            trendScore += 0.2;
        }

        // Search volume factor (higher = more viral potential)
        double volumeFactor = Math.min(keywordData.getSearchVolume() / 50_000.0, 1.0);

        // Combined viral potential
        double viralPotential = (trendScore * 0.7) + (volumeFactor * 0.3);

        return Math.min(viralPotential, 1.0);
    }

    /**
     * Calculates monetization score (0.0 to 1.0).
     * Higher score = better ad revenue potential.
     *
     * @param nicheKeyword the niche keyword
     * @param keywordData keyword trend data
     * @return monetization score
     */
    private double calculateMonetizationScore(String nicheKeyword,
                                             KeywordApiClient.KeywordTrendData keywordData) {
        double score = 0.5; // Base score

        String lowerKeyword = nicheKeyword.toLowerCase();

        // High-value topics (better CPM)
        if (lowerKeyword.contains("money") || lowerKeyword.contains("invest") ||
            lowerKeyword.contains("stock") || lowerKeyword.contains("crypto") ||
            lowerKeyword.contains("business") || lowerKeyword.contains("passive income")) {
            score += 0.3;
        }

        // Tech and productivity (good CPM)
        if (lowerKeyword.contains("tech") || lowerKeyword.contains("productivity") ||
            lowerKeyword.contains("software") || lowerKeyword.contains("app")) {
            score += 0.2;
        }

        // Education and tutorial (decent CPM)
        if (lowerKeyword.contains("tutorial") || lowerKeyword.contains("learn") ||
            lowerKeyword.contains("course") || lowerKeyword.contains("education")) {
            score += 0.15;
        }

        // Lifestyle and entertainment (moderate CPM)
        if (lowerKeyword.contains("lifestyle") || lowerKeyword.contains("luxury") ||
            lowerKeyword.contains("travel") || lowerKeyword.contains("food")) {
            score += 0.1;
        }

        // Search volume factor (more views = more money)
        if (keywordData.getSearchVolume() > 20000) {
            score += 0.1;
        }

        // Music and ASMR (lower CPM but high watch time)
        if (lowerKeyword.contains("music") || lowerKeyword.contains("asmr") ||
            lowerKeyword.contains("sound") || lowerKeyword.contains("ambient")) {
            score -= 0.1; // Lower CPM
        }

        return Math.max(0.0, Math.min(score, 1.0));
    }

    /**
     * Extracts related keywords for a niche.
     *
     * @param nicheKeyword the main niche keyword
     * @return list of related keywords
     */
    private List<String> extractRelatedKeywords(String nicheKeyword) {
        List<String> keywords = new ArrayList<>();
        keywords.add(nicheKeyword);

        // Add variations
        keywords.add(nicheKeyword + " shorts");
        keywords.add(nicheKeyword + " viral");
        keywords.add(nicheKeyword + " trending");

        // Add common related terms based on niche type
        String lower = nicheKeyword.toLowerCase();

        if (lower.contains("music") || lower.contains("sound")) {
            keywords.add(nicheKeyword + " mix");
            keywords.add(nicheKeyword + " playlist");
        } else if (lower.contains("facts") || lower.contains("did you know")) {
            keywords.add(nicheKeyword + " compilation");
            keywords.add("interesting " + nicheKeyword);
        } else if (lower.contains("tips") || lower.contains("hacks")) {
            keywords.add("best " + nicheKeyword);
            keywords.add(nicheKeyword + " tutorial");
        }

        return keywords;
    }

    /**
     * Gets cached niches from database (updated within last N hours).
     *
     * @param hours hours to look back
     * @param minScore minimum score threshold
     * @return list of cached niches
     */
    public List<NicheData> getCachedNiches(int hours, double minScore) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<NicheData> cachedNiches = nicheRepository.findUpdatedAfter(since);

        return cachedNiches.stream()
                .filter(niche -> niche.meetsThreshold(minScore))
                .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
                .collect(Collectors.toList());
    }

    /**
     * Refreshes niche scores (re-analyzes).
     *
     * @param niche the niche to refresh
     * @param regionCode region code
     * @return updated niche
     */
    public NicheData refreshNiche(NicheData niche, String regionCode) {
        logger.info("Refreshing niche: {}", niche.getTopic());

        try {
            List<TrendData> trendingTopics = trendAnalyzer.analyzeTrendingShorts(regionCode, 20);
            List<Video> trendingShorts = youtubeClient.getTrendingShortsFiltered(regionCode, 50);
            NicheData updatedNiche = analyzeNicheWithCache(niche.getTopic(), regionCode,
                    trendingTopics, trendingShorts);

            if (updatedNiche != null && niche.getId() != null) {
                updatedNiche.setId(niche.getId());
                nicheRepository.update(updatedNiche);
            }

            return updatedNiche;

        } catch (IOException e) {
            logger.error("Failed to refresh niche: {}", niche.getTopic(), e);
            return null;
        }
    }

    /**
     * Cleans up old niche data.
     *
     * @param daysOld days to keep
     * @return number of deleted niches
     */
    public int cleanupOldNiches(int daysOld) {
        logger.info("Cleaning up niches older than {} days", daysOld);
        return nicheRepository.deleteOlderThan(daysOld);
    }
}
