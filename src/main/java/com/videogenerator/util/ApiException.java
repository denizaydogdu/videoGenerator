package com.videogenerator.util;

import com.videogenerator.model.ApiProvider;

/**
 * Exception thrown when API calls fail
 */
public class ApiException extends VideoGeneratorException {

    private final ApiProvider provider;
    private final int statusCode;

    public ApiException(ApiProvider provider, String message) {
        super(String.format("[%s] %s", provider.getDisplayName(), message));
        this.provider = provider;
        this.statusCode = -1;
    }

    public ApiException(ApiProvider provider, String message, int statusCode) {
        super(String.format("[%s] %s (Status Code: %d)", provider.getDisplayName(), message, statusCode));
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public ApiException(ApiProvider provider, String message, Throwable cause) {
        super(String.format("[%s] %s", provider.getDisplayName(), message), cause);
        this.provider = provider;
        this.statusCode = -1;
    }

    public ApiProvider getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return statusCode == 429 || statusCode == 500 || statusCode == 503 || statusCode == 504;
    }
}
