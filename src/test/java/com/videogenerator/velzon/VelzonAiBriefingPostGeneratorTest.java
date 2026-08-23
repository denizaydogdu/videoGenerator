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
    void adaptSendsFullBriefingIncludingKurumsalAkisToLlm() throws Exception {
        // Kullanıcının 2026-08-23 kararı: KURUMSAL AKIŞ (kurum adı + yüzde)
        // artık postlarda da aynen paylaşılacak — bu yüzden LLM'e giden
        // metinden ARTIK ÇIKARILMAMALI.
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        generator.adapt(briefing);

        assertTrue(llm.lastUser.contains("KURUMSAL AKIŞ"));
        assertTrue(llm.lastUser.contains("IS %35.8"));
        assertTrue(llm.lastUser.contains("TURK EKONOMI BANKASI"));
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
