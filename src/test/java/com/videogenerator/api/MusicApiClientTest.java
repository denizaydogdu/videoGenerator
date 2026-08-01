package com.videogenerator.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicApiClientTest {
    @Test
    void buildsBodyWithMillisecondsAndModel() {
        String body = MusicApiClient.buildRequestBody("tense ambient", 75, "music_v2");
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("tense ambient", o.get("prompt").getAsString());
        assertEquals(75000, o.get("music_length_ms").getAsInt());
        assertEquals("music_v2", o.get("model_id").getAsString());
    }
}
