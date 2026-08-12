package com.videogenerator.velzon;

import com.google.gson.Gson;
import com.videogenerator.api.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonYoutubeScriptGeneratorTest {
    static final String LLM_JSON = """
        {"scripts":[
          {"narration":"E-fatura sürecini otomatikleştirmek KOBİ'lere zaman kazandırır. Elle veri girişi yerine otomasyon kullanmak hataları azaltır ve muhasebe ekiplerinin daha stratejik işlere odaklanmasını sağlar.",
           "title":"E-fatura otomasyonu neden önemli?",
           "description":"E-fatura sürecinde otomasyonun KOBİ'lere sağladığı avantajlar üzerine kısa bir anlatım.",
           "hashtags":["#efatura","#muhasebe","#kobi","#fintech"],
           "imagePrompt":"photorealistic slow pan across a modern minimalist Turkish office desk at sunrise, no people, no text"},
          {"narration":"Nakit akışını düzenli takip etmek finansal sürprizleri azaltır. Basit bir takip alışkanlığı, KOBİ'lerin öngörülemeyen dönemlerde bile ayakta kalmasına yardımcı olabilir.",
           "title":"Nakit akışı takibi neden kritik?",
           "description":"KOBİ'ler için düzenli nakit akışı takibinin önemine dair kısa bir anlatım.",
           "hashtags":["#finans","#nakitakis","#kobifinans"],
           "imagePrompt":"photorealistic slow zoom on a calm minimalist finance dashboard on a tablet at a wooden desk, no people, no text"}
        ]}""";

    private LlmClient fakeLlm(String json) {
        return (system, user) -> json;
    }

    @Test
    void generatesScriptsAndWritesManifest(@TempDir Path outDir) throws Exception {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(fakeLlm(LLM_JSON));

        List<VelzonYoutubeScriptGenerator.Script> scripts = gen.generateBatch(
                "e-fatura ve KOBİ finans yönetimi", 2, outDir);

        assertEquals(2, scripts.size());
        assertTrue(scripts.get(0).narration().contains("otomasyon"));
        assertTrue(scripts.get(0).title().length() < 100);
        assertEquals("video-01.png", scripts.get(0).imageFile());
        assertEquals("video-02.png", scripts.get(1).imageFile());
        assertFalse(scripts.get(0).hashtags().isEmpty());

        Path manifestPath = outDir.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath));
        var manifest = new Gson().fromJson(Files.readString(manifestPath),
                com.google.gson.JsonArray.class);
        assertEquals(2, manifest.size());
        var first = manifest.get(0).getAsJsonObject();
        assertEquals("video-01.png", first.get("imageFile").getAsString());
        assertTrue(first.get("narration").getAsString().contains("otomasyon"));
        assertTrue(first.get("title").getAsString().length() > 0);
        assertTrue(first.get("hashtags").getAsJsonArray().size() > 0);
        assertFalse(first.get("published").getAsBoolean(),
                "yeni üretilen senaryo henüz yayınlanmamış olmalı");
        assertFalse(first.has("url"), "url henüz yayınlanmadıysa yazılmamalı");
    }

    @Test
    void stripsMarkdownFencesFromLlmResponse(@TempDir Path outDir) throws Exception {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(
                fakeLlm("```json\n" + LLM_JSON + "\n```"));

        List<VelzonYoutubeScriptGenerator.Script> scripts = gen.generateBatch("konu", 2, outDir);

        assertEquals(2, scripts.size());
    }

    @Test
    void rejectsWrongScriptCount(@TempDir Path outDir) {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(fakeLlm(LLM_JSON));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 5, outDir));
    }

    @Test
    void rejectsMissingFields(@TempDir Path outDir) {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(
                fakeLlm("{\"scripts\":[{\"narration\":\"x\"}]}"));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 1, outDir));
    }

    @Test
    void rejectsMissingHashtags(@TempDir Path outDir) {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(fakeLlm("""
            {"scripts":[{"narration":"n","title":"t","description":"d",
              "hashtags":[],"imagePrompt":"p"}]}"""));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 1, outDir));
    }

    @Test
    void rejectsInvalidJson(@TempDir Path outDir) {
        VelzonYoutubeScriptGenerator gen = new VelzonYoutubeScriptGenerator(fakeLlm("not json{{{"));

        assertThrows(IllegalStateException.class,
                () -> gen.generateBatch("konu", 1, outDir));
    }
}
