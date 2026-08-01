package com.videogenerator.api;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GPT-5.x request-shape regression: max_tokens and non-default temperature
 * are rejected by the live API (verified 2026-08-01).
 */
class ChatBodyTest {
    @Test
    void usesMaxCompletionTokensAndNoTemperature() {
        JsonObject body = OpenAiGptClient.buildChatBody("gpt-5.6-luna", 2000, "sys", "user");
        assertEquals("gpt-5.6-luna", body.get("model").getAsString());
        assertEquals(2000, body.get("max_completion_tokens").getAsInt());
        assertFalse(body.has("max_tokens"), "max_tokens is rejected by gpt-5.x");
        assertFalse(body.has("temperature"), "only default temperature is supported");
        assertEquals(2, body.getAsJsonArray("messages").size());
    }
}
