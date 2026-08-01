package com.videogenerator.api;

import com.videogenerator.util.ApiException;

/**
 * Minimal LLM abstraction so services can be tested with fakes
 * and the underlying provider/model can change without touching callers.
 */
public interface LlmClient {
    String complete(String systemPrompt, String userPrompt) throws ApiException;
}
