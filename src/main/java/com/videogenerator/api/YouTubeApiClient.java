package com.videogenerator.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ApiProvider;
import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import com.videogenerator.util.Constants;
import com.videogenerator.util.UploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client for YouTube Data API v3
 * Handles OAuth2 authentication and video uploads
 */
public class YouTubeApiClient {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeApiClient.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "YouTube Shorts Generator";
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube"
    );

    private final Configuration config;
    private YouTube youtubeService;

    public YouTubeApiClient() throws GeneralSecurityException, IOException {
        this.config = Configuration.getInstance();
        this.youtubeService = getService();
    }

    /**
     * Creates and returns the YouTube service with OAuth2 authentication
     */
    private YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Handles OAuth2 authorization
     */
    private Credential authorize(NetHttpTransport httpTransport) throws IOException {
        // Load client secrets
        GoogleClientSecrets clientSecrets = loadClientSecrets();

        // Build flow
        File tokenStorageDir = new File(config.getConfigDir(), "tokens");
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenStorageDir))
                .setAccessType("offline")
                .build();

        // Authorize
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
                .authorize("user");

        logger.info("OAuth2 authorization successful");
        return credential;
    }

    /**
     * Loads Google client secrets from file
     */
    private GoogleClientSecrets loadClientSecrets() throws IOException {
        // First try to load from config directory
        File secretsFile = new File(config.getConfigDir(), Constants.CREDENTIALS_FILE);

        if (!secretsFile.exists()) {
            // Try to create from environment variables
            String clientId = config.getYouTubeClientId();
            String clientSecret = config.getYouTubeClientSecret();

            if (clientId != null && clientSecret != null &&
                !clientId.contains("YOUR_") && !clientSecret.contains("YOUR_")) {

                // Create GoogleClientSecrets programmatically
                GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                        .setClientId(clientId)
                        .setClientSecret(clientSecret);

                GoogleClientSecrets secrets = new GoogleClientSecrets()
                        .setInstalled(details);

                logger.info("Using OAuth credentials from configuration");
                return secrets;
            }

            throw new FileNotFoundException(
                    "Please create " + secretsFile.getAbsolutePath() + " or set " +
                    "YOUTUBE_CLIENT_ID and YOUTUBE_CLIENT_SECRET environment variables"
            );
        }

        try (InputStream in = new FileInputStream(secretsFile)) {
            GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            logger.info("Loaded OAuth credentials from file");
            return secrets;
        }
    }

    /**
     * Uploads a video to YouTube
     */
    public UploadResult uploadVideo(File videoFile, VideoMetadata metadata) throws UploadException {
        logger.info("Uploading video: {} ({})", videoFile.getName(), metadata.getTitle());

        try {
            // Create video snippet
            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(metadata.getTitle());
            snippet.setDescription(metadata.getDescription());
            snippet.setCategoryId(config.getYouTubeCategoryId());

            // Add tags (hashtags without #)
            if (metadata.getYouTubeTags().length > 0) {
                snippet.setTags(Arrays.asList(metadata.getYouTubeTags()));
            }

            // Set video status
            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus(config.getYouTubePrivacyStatus());

            // Mark as Shorts (important for YouTube to recognize it as Shorts)
            // This is done by ensuring video is < 60s and 9:16 aspect ratio

            // Create video with snippet and status
            Video video = new Video();
            video.setSnippet(snippet);
            video.setStatus(status);

            // Create upload stream
            InputStreamContent mediaContent = new InputStreamContent(
                    "video/mp4",
                    new BufferedInputStream(new FileInputStream(videoFile))
            );
            mediaContent.setLength(videoFile.length());

            // Create upload request
            YouTube.Videos.Insert videoInsert = youtubeService.videos()
                    .insert(Arrays.asList("snippet", "status"), video, mediaContent);

            // Set uploader progress listener
            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);
            uploader.setProgressListener(new UploadProgressListener());

            // Execute upload
            logger.info("Starting upload...");
            Video returnedVideo = videoInsert.execute();

            // Create result
            UploadResult result = new UploadResult(
                    returnedVideo.getId(),
                    metadata.getTitle()
            );
            result.setFilePath(videoFile.getAbsolutePath());

            logger.info("Video uploaded successfully: {}", result.getUrl());
            logger.info("Shorts URL: {}", result.getShortsUrl());

            return result;

        } catch (Exception e) {
            logger.error("Error uploading video", e);
            throw new UploadException("Failed to upload video: " + e.getMessage(), e);
        }
    }

    /**
     * Upload progress listener
     */
    private static class UploadProgressListener implements MediaHttpUploaderProgressListener {
        @Override
        public void progressChanged(MediaHttpUploader uploader) throws IOException {
            switch (uploader.getUploadState()) {
                case INITIATION_STARTED:
                    logger.info("Upload initiation started");
                    break;
                case INITIATION_COMPLETE:
                    logger.info("Upload initiation complete");
                    break;
                case MEDIA_IN_PROGRESS:
                    logger.info("Upload progress: {}%",
                            String.format("%.2f", uploader.getProgress() * 100));
                    break;
                case MEDIA_COMPLETE:
                    logger.info("Upload complete!");
                    break;
                case NOT_STARTED:
                    logger.debug("Upload not started");
                    break;
            }
        }
    }

    /**
     * Validates that the video meets YouTube Shorts requirements
     */
    public boolean validateShortsRequirements(File videoFile) {
        // Check file exists and size
        if (!videoFile.exists() || !videoFile.isFile()) {
            logger.error("Video file does not exist: {}", videoFile.getAbsolutePath());
            return false;
        }

        long fileSize = videoFile.length();
        if (fileSize == 0) {
            logger.error("Video file is empty");
            return false;
        }

        if (fileSize > 256L * 1024 * 1024 * 1024) { // 256 GB
            logger.error("Video file is too large: {} bytes", fileSize);
            return false;
        }

        // Additional checks can be added here (duration, resolution)
        // For now, we trust that FFmpeg created the video correctly

        logger.info("Video file validated: {} ({} bytes)", videoFile.getName(), fileSize);
        return true;
    }

    /**
     * Tests the YouTube API connection
     */
    public boolean testConnection() {
        try {
            // Try to list channels to test authentication
            youtubeService.channels()
                    .list(Arrays.asList("snippet"))
                    .setMine(true)
                    .execute();
            logger.info("YouTube API connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("YouTube API connection test failed", e);
            return false;
        }
    }

    /**
     * Gets trending Shorts videos for trend analysis.
     *
     * @param regionCode region code (e.g., "US", "TR", "GB")
     * @param maxResults maximum number of videos to retrieve (1-50)
     * @return list of trending video items
     * @throws IOException if API call fails
     */
    public VideoListResponse getTrendingShorts(String regionCode, int maxResults) throws IOException {
        logger.info("Fetching trending Shorts for region: {}", regionCode);

        // Get trending videos (filtered by short duration)
        YouTube.Videos.List request = youtubeService.videos()
                .list(Arrays.asList("snippet", "statistics", "contentDetails"))
                .setChart("mostPopular")
                .setRegionCode(regionCode)
                .setMaxResults((long) Math.min(maxResults, 50))
                .setVideoCategoryId("0"); // All categories

        VideoListResponse response = request.execute();
        logger.info("Retrieved {} trending videos", response.getItems().size());

        return response;
    }

    /**
     * Searches for Shorts videos by keyword.
     *
     * @param query search query
     * @param maxResults maximum number of results (1-50)
     * @return list of search results
     * @throws IOException if API call fails
     */
    public SearchListResponse searchShorts(String query, int maxResults) throws IOException {
        logger.info("Searching Shorts for query: {}", query);

        // Search for short videos (< 4 minutes, vertical)
        YouTube.Search.List request = youtubeService.search()
                .list(Arrays.asList("snippet"))
                .setQ(query)
                .setType(Arrays.asList("video"))
                .setVideoDuration("short") // Videos less than 4 minutes
                .setMaxResults((long) Math.min(maxResults, 50))
                .setOrder("viewCount"); // Sort by popularity

        SearchListResponse response = request.execute();
        logger.info("Found {} videos for query: {}", response.getItems().size(), query);

        return response;
    }

    /**
     * Gets detailed statistics for a video.
     *
     * @param videoId the YouTube video ID
     * @return video with statistics
     * @throws IOException if API call fails
     */
    public Video getVideoStatistics(String videoId) throws IOException {
        logger.debug("Fetching statistics for video: {}", videoId);

        YouTube.Videos.List request = youtubeService.videos()
                .list(Arrays.asList("snippet", "statistics", "contentDetails"))
                .setId(Arrays.asList(videoId));

        VideoListResponse response = request.execute();

        if (response.getItems().isEmpty()) {
            logger.warn("Video not found: {}", videoId);
            return null;
        }

        return response.getItems().get(0);
    }

    /**
     * Gets popular search topics based on related videos.
     * This helps discover trending topics in a niche.
     *
     * @param seedTopic initial topic to explore
     * @param maxResults maximum results per search
     * @return list of related search results
     * @throws IOException if API call fails
     */
    public List<SearchResult> getRelatedTopics(String seedTopic, int maxResults) throws IOException {
        logger.info("Finding related topics for: {}", seedTopic);

        YouTube.Search.List request = youtubeService.search()
                .list(Arrays.asList("snippet"))
                .setQ(seedTopic)
                .setType(Arrays.asList("video"))
                .setVideoDuration("short")
                .setMaxResults((long) Math.min(maxResults, 50))
                .setOrder("relevance");

        SearchListResponse response = request.execute();
        return response.getItems();
    }

    /**
     * Analyzes trending Shorts to extract common topics and keywords.
     * Filters response to only include actual Shorts (< 60 seconds).
     *
     * @param regionCode region code
     * @param limit max number to analyze
     * @return list of Shorts videos (< 60s)
     * @throws IOException if API call fails
     */
    public List<Video> getTrendingShortsFiltered(String regionCode, int limit) throws IOException {
        VideoListResponse response = getTrendingShorts(regionCode, limit * 2); // Get more to filter
        List<Video> shorts = new ArrayList<>();

        for (Video video : response.getItems()) {
            String duration = video.getContentDetails().getDuration();
            int seconds = parseDuration(duration);

            // Filter for actual Shorts (< 60 seconds)
            if (seconds > 0 && seconds <= 60) {
                shorts.add(video);
                if (shorts.size() >= limit) {
                    break;
                }
            }
        }

        logger.info("Filtered {} actual Shorts from {} trending videos", shorts.size(), response.getItems().size());
        return shorts;
    }

    /**
     * Parses ISO 8601 duration format (PT1M30S) to seconds.
     *
     * @param duration ISO 8601 duration string
     * @return duration in seconds
     */
    private int parseDuration(String duration) {
        try {
            // Simple parser for PT#M#S format
            duration = duration.replace("PT", "");
            int minutes = 0;
            int seconds = 0;

            if (duration.contains("M")) {
                String[] parts = duration.split("M");
                minutes = Integer.parseInt(parts[0]);
                if (parts.length > 1 && parts[1].contains("S")) {
                    seconds = Integer.parseInt(parts[1].replace("S", ""));
                }
            } else if (duration.contains("S")) {
                seconds = Integer.parseInt(duration.replace("S", ""));
            }

            return minutes * 60 + seconds;

        } catch (Exception e) {
            logger.warn("Failed to parse duration: {}", duration);
            return 0;
        }
    }
}
