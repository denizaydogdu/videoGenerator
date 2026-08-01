package com.videogenerator.util;

/**
 * Exception thrown during YouTube upload operations
 */
public class UploadException extends VideoGeneratorException {

    public UploadException(String message) {
        super(message);
    }

    public UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
