package com.videogenerator.velzon;

import com.videogenerator.api.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VelzonAiBriefingPostGeneratorTest {

    private static final String FULL_BRIEFING = """
            TEKNİK GÖRÜNÜM
            30 dakikalık grafikte fiyat düşüş trendinde. RSI 34.6 nötr bölgede.

            TEMEL DURUM
            F/K oranı 3.24 ile düşük seviyelerde.

            KONSENSUS
            Piyasa rejimi bull, THYAO endeksten negatif ayrışıyor.

            KURUMSAL AKIŞ
            AKD verisine göre IS %35.8 alıcı, YATIRIM FINANSMAN %33.1 satıcı.
            TAKAS verisinde TURK EKONOMI BANKASI %47.3 en büyük alıcı.

            RİSK
            ATR oranı %0.31 ile volatilite düşük.

            ÖZET
            Teknik tablo kısa vadede zayıf. [SKOR: SAT]
            """;

    @Test
    void stripKurumsalAkisRemovesOnlyThatSection() {
        String stripped = VelzonAiBriefingPostGenerator.stripKurumsalAkis(FULL_BRIEFING);

        assertTrue(stripped.contains("TEKNİK GÖRÜNÜM"));
        assertTrue(stripped.contains("TEMEL DURUM"));
        assertTrue(stripped.contains("KONSENSUS"));
        assertTrue(stripped.contains("RİSK"));
        assertTrue(stripped.contains("ÖZET"));
        assertFalse(stripped.contains("KURUMSAL AKIŞ"));
        assertFalse(stripped.contains("IS %35.8"));
        assertFalse(stripped.contains("YATIRIM FINANSMAN"));
        assertFalse(stripped.contains("TURK EKONOMI BANKASI"));
    }

    @Test
    void stripKurumsalAkisIsNoOpWhenSectionAbsent() {
        String noSection = "TEKNİK GÖRÜNÜM\nTest.\n\nÖZET\nTest.";
        assertEquals(noSection, VelzonAiBriefingPostGenerator.stripKurumsalAkis(noSection));
    }

    @Test
    void stripKurumsalAkisWorksWhenNotImmediatelyFollowedByRisk() {
        // Django bölüm sırası değişse (RİSK hemen ardından gelmese) bile
        // temizlik hâlâ çalışmalı — RİSK'e hardcode bağımlılık olmamalı.
        String reordered = """
                TEKNİK GÖRÜNÜM
                Test teknik.

                KURUMSAL AKIŞ
                AKD verisine göre IS %35.8 alıcı, YATIRIM FINANSMAN %33.1 satıcı.

                TEMEL DURUM
                Test temel.

                RİSK
                Test risk.

                ÖZET
                Test özet. [SKOR: NOTR]
                """;

        String stripped = VelzonAiBriefingPostGenerator.stripKurumsalAkis(reordered);

        assertTrue(stripped.contains("TEKNİK GÖRÜNÜM"));
        assertTrue(stripped.contains("TEMEL DURUM"));
        assertTrue(stripped.contains("RİSK"));
        assertTrue(stripped.contains("ÖZET"));
        assertFalse(stripped.contains("KURUMSAL AKIŞ"));
        assertFalse(stripped.contains("IS %35.8"));
        assertFalse(stripped.contains("YATIRIM FINANSMAN"));
    }

    @Test
    void stripKurumsalAkisDoesNotStopEarlyOnInlineHeaderWordMention() {
        // KURUMSAL AKIŞ bölümünün gövdesinde "RİSK" kelimesi cümle içinde
        // geçebilir (gerçek başlık satırı değil) — erken kesip institution
        // verisini sızdırmamalı, gerçek RİSK BAŞLIK SATIRINA kadar gitmeli.
        String withInlineRiskWord = """
                TEKNİK GÖRÜNÜM
                Test teknik.

                KURUMSAL AKIŞ
                AKD verisine göre IS %35.8 alıcı, kurumsal akışta RİSK iştahı arttı.
                TURK EKONOMI BANKASI %47.3 en büyük alıcı.

                RİSK
                Test risk bölümü.

                ÖZET
                Test özet. [SKOR: NOTR]
                """;

        String stripped = VelzonAiBriefingPostGenerator.stripKurumsalAkis(withInlineRiskWord);

        assertTrue(stripped.contains("TEKNİK GÖRÜNÜM"));
        assertTrue(stripped.contains("RİSK\nTest risk bölümü"));
        assertTrue(stripped.contains("ÖZET"));
        assertFalse(stripped.contains("KURUMSAL AKIŞ"));
        assertFalse(stripped.contains("IS %35.8"));
        assertFalse(stripped.contains("TURK EKONOMI BANKASI"));
    }

    @Test
    void stripKurumsalAkisWorksWhenSectionIsLast() {
        // KURUMSAL AKIŞ metnin son bölümüyse (ardından hiçbir başlık
        // gelmiyorsa) da metnin sonuna kadar temizlenmeli.
        String kurumsalIsLast = """
                TEKNİK GÖRÜNÜM
                Test teknik.

                KURUMSAL AKIŞ
                AKD verisine göre IS %35.8 alıcı, TURK EKONOMI BANKASI %47.3 en büyük alıcı.
                """;

        String stripped = VelzonAiBriefingPostGenerator.stripKurumsalAkis(kurumsalIsLast);

        assertTrue(stripped.contains("TEKNİK GÖRÜNÜM"));
        assertFalse(stripped.contains("KURUMSAL AKIŞ"));
        assertFalse(stripped.contains("IS %35.8"));
        assertFalse(stripped.contains("TURK EKONOMI BANKASI"));
    }

    static class FakeLlm implements LlmClient {
        String lastSystem;
        String lastUser;
        String response = """
                {"x":"THYAO teknik görünümde zayıf, RSI nötr bölgede. Daha fazla veri için Velzon'da https://www.velzon.tr/terminal/ sayfasını inceleyin. #borsa #bist",
                 "instagramCaption":"THYAO için teknik görünüm ve temel veriler...",
                 "facebookCaption":"THYAO analizi: teknik görünüm zayıf...",
                 "imagePrompt":"photorealistic financial dashboard, no people, no text"}""";

        @Override
        public String complete(String system, String user) {
            lastSystem = system;
            lastUser = user;
            return response;
        }
    }

    @Test
    void adaptGeneratesAllThreePlatformVariants() throws Exception {
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        var adapted = generator.adapt(briefing);

        assertNotNull(adapted.xText());
        assertTrue(adapted.xText().length() <= 280);
        assertTrue(adapted.xText().contains("velzon.tr/terminal"));
        assertFalse(adapted.instagramCaption().isBlank());
        assertFalse(adapted.facebookCaption().isBlank());
        assertFalse(adapted.imagePrompt().isBlank());
    }

    @Test
    void adaptNeverSendsKurumsalAkisTextToLlm() throws Exception {
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        generator.adapt(briefing);

        assertFalse(llm.lastUser.contains("KURUMSAL AKIŞ"));
        assertFalse(llm.lastUser.contains("IS %35.8"));
        assertFalse(llm.lastUser.contains("TURK EKONOMI BANKASI"));
    }

    @Test
    void adaptThrowsOnMalformedLlmResponse() {
        FakeLlm llm = new FakeLlm();
        llm.response = "not json";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        assertThrows(IllegalStateException.class, () -> generator.adapt(briefing));
    }

    @Test
    void adaptThrowsWhenXTextExceeds280CharsAndLlmDidNotShortenIt() {
        FakeLlm llm = new FakeLlm();
        llm.response = "{\"x\":\"" + "x".repeat(300)
                + "\",\"instagramCaption\":\"c\",\"facebookCaption\":\"c\",\"imagePrompt\":\"p\"}";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        assertThrows(IllegalStateException.class, () -> generator.adapt(briefing));
    }
}
