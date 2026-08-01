package com.videogenerator.util;

import com.videogenerator.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Retry policy with exponential backoff
 */
public class RetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);
    private final int maxAttempts;
    private final int initialDelayMs;
    private final int maxDelayMs;

    public RetryPolicy() {
        Configuration config = Configuration.getInstance();
        this.maxAttempts = config.getMaxRetryAttempts();
        this.initialDelayMs = config.getInitialRetryDelay();
        this.maxDelayMs = config.getMaxRetryDelay();
    }

    public RetryPolicy(int maxAttempts, int initialDelayMs, int maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    /**
     * Executes a supplier with retry logic
     */
    public <T> T execute(Supplier<T> operation, String operationName) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.debug("Executing {}: attempt {}/{}", operationName, attempt, maxAttempts);
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Attempt {}/{} failed for {}: {}", attempt, maxAttempts, operationName, e.getMessage());

                if (attempt < maxAttempts && shouldRetry(e)) {
                    int delay = calculateDelay(attempt);
                    logger.info("Retrying {} in {}ms...", operationName, delay);
                    Thread.sleep(delay);
                } else {
                    break;
                }
            }
        }

        logger.error("All {} attempts failed for {}", maxAttempts, operationName);
        throw lastException;
    }

    /**
     * Executes a runnable with retry logic
     */
    public void execute(Runnable operation, String operationName) throws Exception {
        execute(() -> {
            operation.run();
            return null;
        }, operationName);
    }

    /**
     * Determines if an exception is retryable
     */
    private boolean shouldRetry(Exception e) {
        // Retry on ApiException if it's retryable
        if (e instanceof ApiException) {
            return ((ApiException) e).isRetryable();
        }

        // Retry on IOException (network issues)
        if (e instanceof java.io.IOException) {
            return true;
        }

        // Retry on InterruptedException
        if (e instanceof InterruptedException) {
            return true;
        }

        // Don't retry on other exceptions
        return false;
    }

    /**
     * Calculates delay with exponential backoff
     */
    private int calculateDelay(int attempt) {
        int delay = initialDelayMs * (int) Math.pow(2, attempt - 1);
        return Math.min(delay, maxDelayMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
