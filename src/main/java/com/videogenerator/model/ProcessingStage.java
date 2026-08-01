package com.videogenerator.model;

/**
 * Represents the current stage in the video processing pipeline
 */
public enum ProcessingStage {
    INITIALIZATION,
    MUSIC_GENERATION,
    VIDEO_GENERATION,
    MERGING,
    METADATA_GENERATION,
    VALIDATION,
    UPLOADING,
    CLEANUP,
    COMPLETED,
    ERROR
}
