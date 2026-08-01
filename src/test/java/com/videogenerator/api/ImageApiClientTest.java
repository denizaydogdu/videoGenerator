package com.videogenerator.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageApiClientTest {
    @Test
    void buildsCorrectRequestBody() {
        String body = ImageApiClient.buildRequestBody(
            "gpt-image-2", "old lighthouse", "1024x1536", "medium");
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("gpt-image-2", o.get("model").getAsString());
        assertEquals("old lighthouse", o.get("prompt").getAsString());
        assertEquals("1024x1536", o.get("size").getAsString());
        assertEquals("medium", o.get("quality").getAsString());
        assertEquals(1, o.get("n").getAsInt());
    }
}
