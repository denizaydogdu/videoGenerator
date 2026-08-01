package com.videogenerator.api;

import com.videogenerator.util.ApiException;

import java.io.File;
import java.nio.file.Path;

/**
 * Music generation abstraction for testability.
 */
public interface MusicGenerator {
    File generate(String prompt, int durationSeconds, Path out) throws ApiException;
}
