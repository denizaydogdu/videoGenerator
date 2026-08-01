package com.videogenerator.api;

import com.videogenerator.util.ApiException;

import java.io.File;
import java.nio.file.Path;

/**
 * Image generation abstraction so SceneImageService can be tested
 * without paid API calls.
 */
public interface ImageGenerator {
    File generate(String prompt, Path outFile) throws ApiException;
}
