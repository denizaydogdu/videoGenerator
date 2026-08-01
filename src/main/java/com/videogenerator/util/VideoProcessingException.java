package com.videogenerator.util;

/**
 * Exception thrown during video processing (FFmpeg operations)
 */
public class VideoProcessingException extends VideoGeneratorException {

    public VideoProcessingException(String message) {
        super(message);
    }

    public VideoProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
