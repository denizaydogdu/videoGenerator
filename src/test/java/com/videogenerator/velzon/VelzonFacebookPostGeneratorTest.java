package com.videogenerator.velzon;

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

class VelzonFacebookPostGeneratorTest {
    static final String LLM_JSON = """
        {"posts":[
          {"caption":"E-fatura sürecini otomatikleştirmek KOBİ'lere zaman kazandırır. #efatura #muhasebe #kobi #fintech",
           "imagePrompt":"photorealistic modern Turkish office desk with laptop, no people, no text"},
          {"caption":"Nakit akışını düzenli takip etmek finansal sürprizleri azaltır. #finans #nakitakis #kobifinans",
           "imagePrompt":"photorealistic minimalist finance dashboard on a tablet, no people, no text"}
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
    void generatesPostsAndWritesManifest(@TempDir Path outDir) throws Exception {
        List<String> promptsSeen = new ArrayList<>();
        VelzonFacebookPostGenerator gen = new VelzonFacebookPostGenerator(
                fakeLlm(LLM_JSON), fakeImages(promptsSeen));

        List<VelzonFacebookPostGenerator.Post> posts = gen.generateBatch(
                "e-fatura ve KOBİ finans yönetimi", 2, outDir);

        assertEquals(2, posts.size());
        assertTrue(posts.get(0).caption().contains("#efatura"));
        assertTrue(Files.exists(outDir.resolve("post-01.png")));
        assertTrue(Files.exists(outDir.resolve("post-02.png")));
        assertEquals(2, promptsSeen.size());

        Path manifestPath = outDir.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath));
        var manifest = new Gson().fromJson(Files.readString(manifestPath),
                com.google.gson.JsonArray.class);
        assertEquals(2, manifest.size());
        var first = manifest.get(0).getAsJsonObject();
        assertEquals("post-01.png", first.get("file").getAsString());
        assertTrue(first.get("caption").getAsString().contains("#efatura"));
        assertFalse(first.get("published").getAsBoolean(),
                "yeni üretilen gönderi henüz yayınlanmamış olmalı");
    }

    @Test
    void stripsMarkdownFencesFromLlmResponse(@TempDir Path outDir) throws Exception {
        VelzonFacebookPostGenerator gen = new VelzonFacebookPostGenerator(
                fakeLlm("```json\n" + LLM_JSON + "\n```"), fakeImages(new ArrayList<>()));

        List<VelzonFacebookPostGenerator.Post> posts = gen.generateBatch("konu", 2, outDir);

        assertEquals(2, posts.size());
    }

    @Test
    void rejectsWrongPostCount(@TempDir Path outDir) {
        VelzonFacebookPostGenerator gen = new VelzonFacebookPostGenerator(
                fakeLlm(LLM_JSON), fakeImages(new ArrayList<>()));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 5, outDir));
    }

    @Test
    void rejectsMissingFields(@TempDir Path outDir) {
        VelzonFacebookPostGenerator gen = new VelzonFacebookPostGenerator(
                fakeLlm("{\"posts\":[{\"caption\":\"x\"}]}"), fakeImages(new ArrayList<>()));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 1, outDir));
    }

    @Test
    void rejectsInvalidJson(@TempDir Path outDir) {
        VelzonFacebookPostGenerator gen = new VelzonFacebookPostGenerator(
                fakeLlm("not json{{{"), fakeImages(new ArrayList<>()));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 1, outDir));
    }
}
