package com.videogenerator.api;

/**
 * Shared cleanup for LLM responses that should be pure JSON.
 */
public final class LlmJson {
    private LlmJson() {
    }

    /**
     * Strips surrounding markdown code fences (```json ... ```), if present.
     */
    public static String strip(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return s.trim();
    }
}
