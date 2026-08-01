package com.videogenerator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration manager
 * Loads settings from application.properties file
 */
public class Configuration {
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);
    private static Configuration instance;
    private final Properties properties;

    private Configuration() {
        properties = new Properties();
        loadProperties();
    }

    public static synchronized Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }

    private void loadProperties() {
        // Try loading from config directory first
        try (InputStream input = new FileInputStream("config/application.properties")) {
            properties.load(input);
            logger.info("Configuration loaded from config/application.properties");
            return;
        } catch (IOException e) {
            logger.debug("Could not load from config/application.properties: {}", e.getMessage());
        }

        // Try loading from classpath
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                logger.info("Configuration loaded from classpath");
                return;
            }
        } catch (IOException e) {
            logger.error("Error loading properties from classpath", e);
        }

        logger.warn("No configuration file found, using defaults");
    }

    public String get(String key) {
        // First try environment variable
        String envKey = key.replace('.', '_').toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // Then try properties file
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        String value = get(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    // API Keys

    public String getOpenAiApiKey() {
        return get("openai.api.key");
    }

    public String getYouTubeClientId() {
        return get("youtube.client.id");
    }

    public String getYouTubeClientSecret() {
        return get("youtube.client.secret");
    }

    // Scheduler Settings
    public boolean isSchedulerEnabled() {
        return getBoolean("app.scheduler.enabled", true);
    }

    public String getSchedulerCron() {
        return get("app.scheduler.cron", "0 12 * * *");
    }

    public String getSchedulerTimezone() {
        return get("app.scheduler.timezone", "UTC");
    }

    // Video Settings
    public int getVideoWidth() {
        return getInt("video.width", 1080);
    }

    public int getVideoHeight() {
        return getInt("video.height", 1920);
    }

    // Shorts factory settings
    public String getLlmModel()         { return get("llm.model", "gpt-5.6-luna"); }
    public String getImageModel()       { return get("image.model", "gpt-image-2"); }
    public String getImageQuality()     { return get("image.quality", "medium"); }
    public String getImageSize()        { return get("image.size", "1024x1536"); }
    public String getMusicModel()       { return get("music.model", "music_v2"); }
    public double getMonthlyBudgetUsd() { return getDouble("budget.monthly.usd", 100.0); }
    public int getBackofficePort()      { return getInt("backoffice.port", 8080); }
    public String getChannelsDir()      { return get("channels.dir", "channels"); }
    public String getJobsDir()          { return get("jobs.dir", "output/jobs"); }
    public String getCostsDir()         { return get("costs.dir", "output/costs"); }

    public int getVideoFps() {
        return getInt("video.fps", 30);
    }

    public int getMaxVideoDuration() {
        return getInt("video.max.duration", 180);
    }

    // FFmpeg Paths
    public String getFfmpegPath() {
        return get("ffmpeg.path", "ffmpeg");
    }

    public String getFfprobePath() {
        return get("ffprobe.path", "ffprobe");
    }

    // Directory Settings
    public String getOutputDir() {
        return get("dir.output", "output");
    }

    public String getTempDir() {
        return get("dir.temp", "temp");
    }

    public String getLogsDir() {
        return get("dir.logs", "logs");
    }

    public String getConfigDir() {
        return get("dir.config", "config");
    }

    // Content Generation
    public String getDefaultMusicGenre() {
        return get("music.default.genre", "electronic");
    }

    public int getDefaultMusicDuration() {
        return getInt("music.default.duration", 20);
    }

    public String getDefaultVideoMood() {
        return get("video.default.mood", "energetic");
    }

    // YouTube Settings
    public String getYouTubeCategoryId() {
        return get("youtube.category.id", "10");
    }

    public String getYouTubePrivacyStatus() {
        return get("youtube.privacy.status", "public");
    }

    // API Limits

    public int getYouTubeDailyLimit() {
        return getInt("api.youtube.daily.limit", 6);
    }

    public int getOpenAiRpmLimit() {
        return getInt("api.openai.rpm.limit", 60);
    }

    // Retry Configuration
    public int getMaxRetryAttempts() {
        return getInt("retry.max.attempts", 3);
    }

    public int getInitialRetryDelay() {
        return getInt("retry.initial.delay.ms", 1000);
    }

    public int getMaxRetryDelay() {
        return getInt("retry.max.delay.ms", 8000);
    }

    // Timeouts
    public int getHttpConnectTimeout() {
        return getInt("http.connect.timeout.seconds", 30);
    }

    public int getHttpReadTimeout() {
        return getInt("http.read.timeout.seconds", 60);
    }



    // Niche Finder Configuration
    public boolean isNicheFinderEnabled() {
        return getBoolean("niche.finder.enabled", true);
    }

    public String getNicheTrendRegion() {
        return get("niche.trend.region", "US");
    }

    public int getNicheUpdateIntervalHours() {
        return getInt("niche.update.interval.hours", 24);
    }

    public double getNicheMinScore() {
        return getDouble("niche.min.score", 0.6);
    }

    public int getNicheDiscoveryCount() {
        return getInt("niche.discovery.count", 5);
    }

    public int getTrendAnalysisLimit() {
        return getInt("trend.analysis.limit", 30);
    }

    public double getTrendMinScore() {
        return getDouble("trend.min.score", 0.5);
    }

    // Idea Generator Configuration
    public int getIdeaGeneratorCount() {
        return getInt("idea.generator.count", 10);
    }

    public double getIdeaMinCtr() {
        return getDouble("idea.min.ctr", 0.5);
    }

    public boolean isIdeaSeoOptimize() {
        return getBoolean("idea.seo.optimize", true);
    }

    public boolean isIdeaIncludeHooks() {
        return getBoolean("idea.include.hooks", true);
    }

    // Text-to-Speech Configuration
    public String getTtsProvider() {
        return get("tts.provider", "elevenlabs");
    }

    public String getTtsApiKey() {
        return get("tts.api.key", System.getenv("ELEVENLABS_API_KEY"));
    }

    public String getTtsVoiceId() {
        return get("tts.voice.id", "21m00Tcm4TlvDq8ikWAM"); // Rachel default
    }

    public String getTtsModel() {
        return get("tts.model", "eleven_v3");
    }

    public double getTtsStability() {
        return getDouble("tts.stability", 0.5);
    }

    public double getTtsSimilarityBoost() {
        return getDouble("tts.similarity.boost", 0.75);
    }

    public boolean isTtsEnabled() {
        return getBoolean("tts.enabled", true);
    }

    // Script Writer Configuration
    public int getScriptTargetDuration() {
        return getInt("script.target.duration.seconds", 55);
    }

    public double getScriptWordsPerSecond() {
        return getDouble("script.words.per.second", 2.5);
    }

    public int getScriptTargetWords() {
        return (int) (getScriptTargetDuration() * getScriptWordsPerSecond());
    }

    public String getScriptStyle() {
        return get("script.style", "conversational");
    }

    public boolean isScriptIncludePauses() {
        return getBoolean("script.include.pauses", true);
    }

    // Audio Mixing Configuration
    public double getAudioVoiceoverVolume() {
        return getDouble("audio.voiceover.volume", 1.0);
    }

    public double getAudioMusicVolume() {
        return getDouble("audio.music.volume", 0.25);
    }

    public boolean isAudioDuckingEnabled() {
        return getBoolean("audio.ducking.enabled", true);
    }

    public double getAudioDuckingAmount() {
        return getDouble("audio.ducking.amount", -10.0);
    }

    public boolean isAudioNormalize() {
        return getBoolean("audio.normalize", true);
    }

    /**
     * Validates that all required API keys are present
     * @throws IllegalStateException if any required key is missing
     */
    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (getOpenAiApiKey() == null || getOpenAiApiKey().contains("YOUR_")) {
            errors.append("- OpenAI API key is not configured\n");
        }

        String ttsKey = getTtsApiKey();
        if (ttsKey == null || ttsKey.isEmpty() || ttsKey.contains("YOUR_")) {
            errors.append("- ElevenLabs API key is not configured (tts.api.key)\n");
        }

        if (errors.length() > 0) {
            throw new IllegalStateException("Configuration validation failed:\n" + errors.toString());
        }

        logger.info("Configuration validation successful");
    }
}
