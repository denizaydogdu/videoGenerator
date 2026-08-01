package com.videogenerator.util;

/**
 * Base exception for all video generator errors
 */
public class VideoGeneratorException extends Exception {

    public VideoGeneratorException(String message) {
        super(message);
    }

    public VideoGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
