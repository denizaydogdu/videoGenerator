package com.videogenerator.model;

/**
 * Represents the status of a video in the generation pipeline
 */
public enum VideoStatus {
    PENDING("Pending", "Video generation is queued"),
    MUSIC_GENERATING("Music Generating", "AI music is being generated"),
    VIDEO_GENERATING("Video Generating", "AI video is being generated"),
    PROCESSING("Processing", "Video and audio are being merged"),
    METADATA_GENERATING("Metadata Generating", "Title, description, and hashtags are being generated"),
    UPLOADING("Uploading", "Video is being uploaded to YouTube"),
    COMPLETED("Completed", "Video successfully uploaded"),
    FAILED("Failed", "Video generation or upload failed");

    private final String displayName;
    private final String description;

    VideoStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
