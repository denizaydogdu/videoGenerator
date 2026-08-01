package com.videogenerator.channel;

import java.util.List;

/**
 * Channel profile loaded from channels/<id>.json.
 * One profile per channel; adding a channel means adding a JSON file + OAuth token.
 */
public class ChannelProfile {
    private String channelId;
    private String displayName;
    private String stylePrefix;
    private String voiceId;
    private String youtubeTokenFile;
    private NicheSpec niche;
    private List<String> languages;
    private List<String> platforms;
    private int targetDurationSeconds;
    private int sceneCount;
    private boolean enabled;

    /**
     * Validates required fields and value ranges.
     *
     * @throws IllegalArgumentException if any field is missing or out of range
     */
    public void validate() {
        require(channelId != null && !channelId.isBlank(), "channelId");
        require(voiceId != null && !voiceId.isBlank(), "voiceId");
        require(niche != null && niche.getTopic() != null, "niche.topic");
        require(languages != null && !languages.isEmpty(), "languages");
        require(targetDurationSeconds >= 60 && targetDurationSeconds <= 90,
                "targetDurationSeconds must be 60-90 (TikTok >=60s rule)");
        require(sceneCount >= 3 && sceneCount <= 10, "sceneCount must be 3-10");
    }

    private static void require(boolean ok, String field) {
        if (!ok) {
            throw new IllegalArgumentException("Invalid channel profile: " + field);
        }
    }

    public String getChannelId() {
        return channelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStylePrefix() {
        return stylePrefix;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public String getYoutubeTokenFile() {
        return youtubeTokenFile;
    }

    public NicheSpec getNiche() {
        return niche;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<String> getPlatforms() {
        return platforms;
    }

    public int getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
