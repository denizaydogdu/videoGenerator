package com.videogenerator.pinterest;

import com.google.gson.Gson;
import com.videogenerator.api.ImageGenerator;
import com.videogenerator.api.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PinterestBatchGeneratorTest {
    static final String LLM_JSON = """
        {"pins":[
          {"title":"Tiny Living Room Before & After",
           "imagePrompt":"photorealistic staged living room, no people",
           "description":"See how a few swaps transform a cramped room. #homedecor #smallspace",
           "altText":"Small living room with compact sofa and layered lighting"},
          {"title":"Best 5 Products for a Cozy Bedroom",
           "imagePrompt":"photorealistic staged bedroom, no people",
           "description":"Five practical finds for a restful bedroom. #bedroomdecor #cozyhome",
           "altText":"Cozy small bedroom with storage bed and slim nightstand"}
        ]}""";

    private LlmClient fakeLlm(String json) {
        return (system, user) -> json;
    }

    private ImageGenerator fakeImages(List<String> promptsSeen) {
        return (prompt, outFile) -> {
            promptsSeen.add(prompt);
            try {
                Files.writeString(outFile, "png-bytes");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return outFile.toFile();
        };
    }

    @Test
    void generatesPinsAndWritesManifest(@TempDir Path outDir) throws Exception {
        List<String> promptsSeen = new ArrayList<>();
        PinterestBatchGenerator gen = new PinterestBatchGenerator(
                fakeLlm(LLM_JSON), fakeImages(promptsSeen));

        List<PinterestBatchGenerator.Pin> pins = gen.generateBatch(
                "cozy small-space home decor", 2, outDir);

        assertEquals(2, pins.size());
        assertEquals("Tiny Living Room Before & After", pins.get(0).title());
        assertTrue(Files.exists(outDir.resolve("pin-01.png")));
        assertTrue(Files.exists(outDir.resolve("pin-02.png")));
        assertEquals(2, promptsSeen.size());

        Path manifestPath = outDir.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath));
        var manifest = new Gson().fromJson(Files.readString(manifestPath),
                com.google.gson.JsonArray.class);
        assertEquals(2, manifest.size());
        var first = manifest.get(0).getAsJsonObject();
        assertEquals("pin-01.png", first.get("file").getAsString());
        assertEquals("Small living room with compact sofa and layered lighting",
                first.get("altText").getAsString());
        assertFalse(first.get("published").getAsBoolean(),
                "yeni üretilen pin henüz yayınlanmamış olmalı");
        assertEquals("Small living room with compact sofa and layered lighting",
                pins.get(0).altText());
    }

    @Test
    void stripsMarkdownFencesFromLlmResponse(@TempDir Path outDir) throws Exception {
        PinterestBatchGenerator gen = new PinterestBatchGenerator(
                fakeLlm("```json\n" + LLM_JSON + "\n```"), fakeImages(new ArrayList<>()));

        List<PinterestBatchGenerator.Pin> pins = gen.generateBatch("niche", 2, outDir);

        assertEquals(2, pins.size());
    }

    @Test
    void rejectsWrongPinCount(@TempDir Path outDir) {
        PinterestBatchGenerator gen = new PinterestBatchGenerator(
                fakeLlm(LLM_JSON), fakeImages(new ArrayList<>()));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("niche", 5, outDir));
    }

    @Test
    void rejectsMissingFields(@TempDir Path outDir) {
        PinterestBatchGenerator gen = new PinterestBatchGenerator(
                fakeLlm("{\"pins\":[{\"title\":\"x\"}]}"), fakeImages(new ArrayList<>()));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("niche", 1, outDir));
    }
}
