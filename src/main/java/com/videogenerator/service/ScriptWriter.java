package com.videogenerator.service;

import com.videogenerator.api.OpenAiGptClient;
import com.videogenerator.config.Configuration;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.VoiceoverScript;
import com.videogenerator.util.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for generating TTS-optimized voiceover scripts for YouTube Shorts.
 * Creates 150-200 word scripts structured as: Hook → Body → CTA.
 */
public class ScriptWriter {
    private static final Logger logger = LoggerFactory.getLogger(ScriptWriter.class);
    private final OpenAiGptClient gptClient;
    private final Configuration config;

    // Script timing constants (for 60-second Shorts)
    private static final int TARGET_DURATION = 55;  // Target 55 seconds (5 second safety margin)
    private static final double WORDS_PER_SECOND = 2.5;  // Average speaking rate
    private static final int TARGET_WORDS = (int) (TARGET_DURATION * WORDS_PER_SECOND); // ~137 words

    public ScriptWriter(OpenAiGptClient gptClient) {
        this.gptClient = gptClient;
        this.config = Configuration.getInstance();
    }

    /**
     * Generates a voiceover script from a content idea.
     *
     * @param idea the content idea
     * @return voiceover script
     * @throws ApiException if generation fails
     */
    public VoiceoverScript generateScript(ContentIdea idea) throws ApiException {
        logger.info("Generating script for: {}", idea.getTitle());

        String systemPrompt = "You are a professional YouTube Shorts scriptwriter. You specialize in " +
                "creating engaging, conversational voiceover scripts that are optimized for AI text-to-speech. " +
                "Your scripts are fast-paced, use simple language, short sentences, and keep viewers hooked. " +
                "Generate scripts in a structured format.";

        String userPrompt = String.format(
                "Write a %d-second voiceover script for this YouTube Shorts video:\n\n" +
                "Title: %s\n" +
                "Hook: %s\n" +
                "Description: %s\n\n" +
                "Script Requirements:\n" +
                "- Target: %d words (for %.1f words/second speaking rate)\n" +
                "- Structure: Hook (15-20 words) → Body (100-130 words) → Call-to-Action (10-15 words)\n" +
                "- Tone: Conversational, energetic, engaging\n" +
                "- Language: Simple, everyday words (avoid jargon)\n" +
                "- Sentences: Short (5-12 words each)\n" +
                "- TTS-friendly: No complex punctuation, write how people talk\n" +
                "- Pacing: Fast, keep viewers engaged\n\n" +
                "Hook (First 3-5 seconds):\n" +
                "- Grab attention immediately\n" +
                "- Use the provided hook as inspiration\n" +
                "- Create curiosity or shock\n\n" +
                "Body (Next 45-50 seconds):\n" +
                "- Deliver on the hook's promise\n" +
                "- Use numbered points if listing things\n" +
                "- Build excitement or suspense\n" +
                "- Include mini-hooks to maintain attention\n" +
                "- Natural pauses between ideas\n\n" +
                "Call-to-Action (Final 5 seconds):\n" +
                "- Ask for like/subscribe\n" +
                "- Tease next video\n" +
                "- Keep it short and punchy\n\n" +
                "Format your response as:\n" +
                "HOOK:\n" +
                "[hook text here]\n\n" +
                "BODY:\n" +
                "[body text here]\n\n" +
                "CTA:\n" +
                "[call to action here]\n\n" +
                "Return ONLY the script in this format, no additional commentary.",
                TARGET_DURATION, idea.getTitle(), idea.getHook(),
                idea.getDescription(), TARGET_WORDS, WORDS_PER_SECOND
        );

        try {
            String response = chatCompletion(systemPrompt, userPrompt);

            // Parse the structured response
            VoiceoverScript script = parseScriptResponse(response);

            // Validate script
            if (!script.isValid()) {
                logger.warn("Generated script is invalid, retrying with adjusted parameters");
                // Retry with shorter target
                return retryScriptGeneration(idea, TARGET_WORDS - 20);
            }

            // Check duration
            if (!script.fitsTargetDuration()) {
                logger.warn("Script duration ({} seconds) exceeds target ({}), adjusting",
                        script.getEstimatedDuration(), TARGET_DURATION);
                return retryScriptGeneration(idea, TARGET_WORDS - 30);
            }

            logger.info("Script generated: {} words, estimated {} seconds",
                    script.getEstimatedWordCount(), script.getEstimatedDuration());

            return script;

        } catch (Exception e) {
            logger.error("Failed to generate script", e);
            throw new ApiException(null, "Script generation failed", e);
        }
    }

    /**
     * Retries script generation with adjusted word count.
     *
     * @param idea content idea
     * @param targetWords adjusted word count
     * @return voiceover script
     * @throws ApiException if retry fails
     */
    private VoiceoverScript retryScriptGeneration(ContentIdea idea, int targetWords) throws ApiException {
        logger.info("Retrying script generation with {} words target", targetWords);

        String systemPrompt = "You are a YouTube Shorts scriptwriter. Create concise, punchy scripts.";

        String userPrompt = String.format(
                "Write a CONCISE %d-word voiceover script for: %s\n\n" +
                "Hook: %s\n\n" +
                "Format:\n" +
                "HOOK: [15 words]\n" +
                "BODY: [%d words]\n" +
                "CTA: [10 words]\n\n" +
                "Use short sentences. Be direct. No fluff.",
                targetWords, idea.getTitle(), idea.getHook(), targetWords - 25
        );

        String response = chatCompletion(systemPrompt, userPrompt);
        return parseScriptResponse(response);
    }

    /**
     * Parses GPT response into VoiceoverScript.
     *
     * @param response GPT response
     * @return voiceover script
     */
    private VoiceoverScript parseScriptResponse(String response) {
        String hook = "";
        String body = "";
        String cta = "";

        // Parse structured format
        String[] sections = response.split("(?i)(HOOK:|BODY:|CTA:)");

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;

            if (response.toLowerCase().contains("hook:") && hook.isEmpty() && i > 0) {
                hook = section;
            } else if (response.toLowerCase().contains("body:") && body.isEmpty() && i > 1) {
                body = section;
            } else if (response.toLowerCase().contains("cta:") && cta.isEmpty() && i > 2) {
                cta = section;
            }
        }

        // Fallback: if structured parsing failed, split by paragraphs
        if (hook.isEmpty() || body.isEmpty()) {
            String[] paragraphs = response.split("\n\n+");
            if (paragraphs.length >= 3) {
                hook = paragraphs[0].replaceAll("(?i)^HOOK:\\s*", "").trim();
                body = paragraphs[1].replaceAll("(?i)^BODY:\\s*", "").trim();
                cta = paragraphs[2].replaceAll("(?i)^CTA:\\s*", "").trim();
            } else if (paragraphs.length == 2) {
                hook = paragraphs[0].trim();
                body = paragraphs[1].trim();
                cta = "Like and subscribe for more!";
            } else {
                // Use entire response as body, generate hook and CTA
                body = response.trim();
                hook = body.substring(0, Math.min(100, body.length()));
                cta = "Don't forget to subscribe!";
            }
        }

        VoiceoverScript script = new VoiceoverScript(hook, body, cta);
        script.setTargetDurationSeconds(TARGET_DURATION);

        return script;
    }

    /**
     * Makes a chat completion request to GPT.
     *
     * @param systemMessage system message
     * @param userMessage user message
     * @return GPT response
     * @throws ApiException if request fails
     */
    private String chatCompletion(String systemMessage, String userMessage) throws ApiException {
        return gptClient.generateCustomScript(systemMessage, userMessage);
    }

    /**
     * Optimizes script for TTS (removes complex punctuation, etc.).
     *
     * @param script the script to optimize
     * @return optimized script
     */
    public VoiceoverScript optimizeForTTS(VoiceoverScript script) {
        String hook = optimizeText(script.getHook());
        String body = optimizeText(script.getBody());
        String cta = optimizeText(script.getCallToAction());

        VoiceoverScript optimized = new VoiceoverScript(hook, body, cta);
        optimized.setTargetDurationSeconds(script.getTargetDurationSeconds());

        logger.info("Script optimized for TTS");
        return optimized;
    }

    /**
     * Optimizes text for TTS rendering.
     *
     * @param text the text to optimize
     * @return optimized text
     */
    private String optimizeText(String text) {
        if (text == null) return "";

        return text
                // Remove complex punctuation
                .replaceAll("[;:]", ",")
                // Replace em dashes with commas
                .replaceAll("—", ",")
                // Remove parentheses
                .replaceAll("[\\(\\)]", "")
                // Ensure single spaces
                .replaceAll("\\s+", " ")
                // Remove quotes
                .replaceAll("[\"']", "")
                .trim();
    }

    /**
     * Validates a script meets all requirements.
     *
     * @param script the script to validate
     * @return true if valid
     */
    public boolean validateScript(VoiceoverScript script) {
        if (!script.isValid()) {
            logger.warn("Script validation failed: basic validity check");
            return false;
        }

        if (!script.fitsTargetDuration()) {
            logger.warn("Script validation failed: duration {} exceeds target {}",
                    script.getEstimatedDuration(), script.getTargetDurationSeconds());
            return false;
        }

        int wordCount = script.getEstimatedWordCount();
        if (wordCount < 120 || wordCount > 200) {
            logger.warn("Script validation failed: word count {} out of range (120-200)", wordCount);
            return false;
        }

        return true;
    }
}
