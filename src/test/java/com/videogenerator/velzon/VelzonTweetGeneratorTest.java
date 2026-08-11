package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.videogenerator.api.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonTweetGeneratorTest {
    static final String LLM_JSON = """
        {"tweets":[
          {"topic":"e-invoicing basics","text":"E-fatura sürecinizi otomatikleştirmek, zaman kaybını azaltır ve hata riskini düşürür. Küçük işletmeler için pratik bir başlangıç adımı."},
          {"topic":"automation benefits","text":"Muhasebe süreçlerini otomatikleştirmek, KOBİ'lerin büyümeye daha çok zaman ayırmasını sağlar."}
        ]}""";

    private LlmClient fakeLlm(String json) {
        return (system, user) -> json;
    }

    @Test
    void generatesTweetsAndWritesManifest(@TempDir Path outDir) throws Exception {
        VelzonTweetGenerator gen = new VelzonTweetGenerator(fakeLlm(LLM_JSON));

        List<VelzonTweetGenerator.Tweet> tweets = gen.generateBatch("fintech eğitici içerik", 2, outDir);

        assertEquals(2, tweets.size());
        assertTrue(tweets.get(0).text().startsWith("E-fatura"));

        Path manifestPath = outDir.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath));
        var manifest = new Gson().fromJson(Files.readString(manifestPath),
                com.google.gson.JsonArray.class);
        assertEquals(2, manifest.size());
        var first = manifest.get(0).getAsJsonObject();
        assertTrue(first.get("text").getAsString().startsWith("E-fatura"));
        assertFalse(first.get("published").getAsBoolean());
    }

    @Test
    void stripsMarkdownFencesFromLlmResponse(@TempDir Path outDir) throws Exception {
        VelzonTweetGenerator gen = new VelzonTweetGenerator(fakeLlm("```json\n" + LLM_JSON + "\n```"));

        List<VelzonTweetGenerator.Tweet> tweets = gen.generateBatch("niche", 2, outDir);

        assertEquals(2, tweets.size());
    }

    @Test
    void rejectsWrongTweetCount(@TempDir Path outDir) {
        VelzonTweetGenerator gen = new VelzonTweetGenerator(fakeLlm(LLM_JSON));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("niche", 5, outDir));
    }

    @Test
    void rejectsTweetOver280Chars(@TempDir Path outDir) {
        String longText = "x".repeat(281);
        String json = "{\"tweets\":[{\"topic\":\"t\",\"text\":\"" + longText + "\"}]}";
        VelzonTweetGenerator gen = new VelzonTweetGenerator(fakeLlm(json));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("niche", 1, outDir));
    }

    @Test
    void rejectsMissingFields(@TempDir Path outDir) {
        VelzonTweetGenerator gen = new VelzonTweetGenerator(
                fakeLlm("{\"tweets\":[{\"topic\":\"x\"}]}"));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("niche", 1, outDir));
    }
}
