package com.videogenerator.util;

/**
 * Application-wide constants
 */
public class Constants {

    // API Endpoints
    public static final String SUNO_API_BASE_URL = "https://api.sunoapi.org";
    public static final String SUNO_GENERATE_ENDPOINT = "/api/generate";
    public static final String SUNO_QUERY_ENDPOINT = "/api/query";

    public static final String OPENAI_API_BASE_URL = "https://api.openai.com/v1";
    public static final String OPENAI_CHAT_ENDPOINT = "/chat/completions";
    public static final String OPENAI_SORA_ENDPOINT = "/video/generations";

    public static final String YOUTUBE_API_BASE_URL = "https://www.googleapis.com/youtube/v3";
    public static final String YOUTUBE_UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos";

    // Video Specifications (YouTube Shorts)
    public static final int VIDEO_WIDTH = 720;
    public static final int VIDEO_HEIGHT = 1280;
    public static final int VIDEO_FPS = 30;
    public static final int MAX_VIDEO_DURATION_SECONDS = 59;
    public static final String VIDEO_FORMAT = "mp4";
    public static final String AUDIO_FORMAT = "mp3";

    // File Paths
    public static final String OUTPUT_DIR = "output";
    public static final String TEMP_DIR = "temp";
    public static final String LOGS_DIR = "logs";
    public static final String CONFIG_DIR = "config";

    // Configuration File Names
    public static final String CONFIG_FILE = "application.properties";
    public static final String CREDENTIALS_FILE = "google_credentials.json";
    public static final String TOKENS_FILE = "tokens.json";
    public static final String HISTORY_FILE = "video_history.json";

    // Retry Configuration
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final int INITIAL_RETRY_DELAY_MS = 1000;
    public static final int MAX_RETRY_DELAY_MS = 8000;

    // API Rate Limits
    public static final int SUNO_DAILY_LIMIT = 50;
    public static final int YOUTUBE_DAILY_LIMIT = 6;
    public static final int OPENAI_RPM_LIMIT = 60;

    // Timeouts
    public static final int HTTP_CONNECT_TIMEOUT_SECONDS = 30;
    public static final int HTTP_READ_TIMEOUT_SECONDS = 60;
    public static final int SUNO_POLLING_INTERVAL_MS = 5000;
    public static final int SUNO_MAX_WAIT_TIME_MS = 300000; // 5 minutes

    // FFmpeg
    public static final String FFMPEG_COMMAND = "ffmpeg";
    public static final String FFPROBE_COMMAND = "ffprobe";

    // Default Values
    public static final String DEFAULT_MUSIC_GENRE = "electronic";
    public static final int DEFAULT_MUSIC_DURATION = 20;
    public static final String DEFAULT_VIDEO_MOOD = "energetic";

    // YouTube Settings
    public static final String YOUTUBE_CATEGORY_ID = "10"; // Music category
    public static final String YOUTUBE_PRIVACY_STATUS = "public";
    public static final String YOUTUBE_SCOPES = "https://www.googleapis.com/auth/youtube.upload";

    // Logging
    public static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";

    // Estimated API costs (USD) for budget guard estimates
    public static final double COST_IMAGE_MEDIUM = 0.08;
    public static final double COST_TTS_PER_1K_CHARS = 0.10;
    public static final double COST_MUSIC_TRACK = 0.50;
    public static final double COST_LLM_CALL = 0.01;

    private Constants() {
        // Utility class
    }
}
