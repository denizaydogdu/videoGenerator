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
    void adaptPrioritizesKurumsalAkisForXWhenChooserTrueAndDataPresent() throws Exception {
        // Kullanıcının 2026-08-24 talebi: X tweet'i her zaman teknik özete
        // öncelik vermesin, bazen KURUMSAL AKIŞ da öne çıksın.
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm, () -> true);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        generator.adapt(briefing);

        assertTrue(llm.lastUser.contains("ÖNCELİK KURUMSAL AKIŞ"));
    }

    @Test
    void adaptPrioritizesTeknikForXWhenChooserFalse() throws Exception {
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm, () -> false);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        generator.adapt(briefing);

        assertTrue(llm.lastUser.contains("ÖNCELİK TEKNİK GÖRÜNÜM"));
    }

    @Test
    void defaultConstructorAlternatesFocusAcrossConsecutiveCallsNeverTwiceInARow() throws Exception {
        // Kullanıcının endişesi: bağımsız %50 rastgelelikte arka arkaya
        // birkaç tur şansla hep "teknik" çıkabilir. Varsayılan seçici artık
        // KESİN alternate eder — 20 ardışık çağrıda iki aynı seçim yan yana
        // gelmemeli.
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        boolean previousWasKurumsal = false;
        boolean first = true;
        for (int i = 0; i < 20; i++) {
            generator.adapt(briefing);
            boolean isKurumsal = llm.lastUser.contains("ÖNCELİK KURUMSAL AKIŞ");
            assertTrue(isKurumsal || llm.lastUser.contains("ÖNCELİK TEKNİK GÖRÜNÜM"),
                    "odak talimatı beklenen iki metinden biri değil");
            if (!first) {
                assertNotEquals(previousWasKurumsal, isKurumsal,
                        "arka arkaya aynı odak seçildi (tur " + i + ")");
            }
            previousWasKurumsal = isKurumsal;
            first = false;
        }
    }

    @Test
    void adaptForcesTeknikPriorityWhenNoInstitutionalDataEvenIfChooserTrue() throws Exception {
        // Chooser "kurumsal akışa öncelik ver" dese bile, veri yoksa
        // (Django'nun "bağlamda yok" tek cümlesi) öncelik verilecek bir
        // şey yok — teknik odağa zorla düşülmeli.
        String noInstitutionalData = """
                TEKNİK GÖRÜNÜM
                30 dakikalık grafikte fiyat düşüş trendinde. RSI 34.6 nötr bölgede.

                TEMEL DURUM
                F/K oranı 3.24 ile düşük seviyelerde.

                KONSENSUS
                Piyasa rejimi bull, THYAO endeksten negatif ayrışıyor.

                KURUMSAL AKIŞ
                Takas/AKD verisi bağlamda yok.

                RİSK
                ATR oranı %0.31 ile volatilite düşük.

                ÖZET
                Teknik tablo kısa vadede zayıf. [SKOR: SAT]
                """;
        FakeLlm llm = new FakeLlm();
        var generator = new VelzonAiBriefingPostGenerator(llm, () -> true);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", noInstitutionalData);

        generator.adapt(briefing);

        assertTrue(llm.lastUser.contains("ÖNCELİK TEKNİK GÖRÜNÜM"));
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

    @Test
    void adaptThrowsWhenXTextDoesNotMentionSymbol() {
        // 2026-08-24 canlı bug: KURUMSAL AKIŞ odağı istenince LLM sembolü
        // hiç yazmadan attı — okuyucu hangi hisseden bahsedildiğini bilemedi.
        FakeLlm llm = new FakeLlm();
        llm.response = "{\"x\":\"Kurumsal akış: BANK OF AMERICA %40.8, +218.064 lot. "
                + "Daha fazla veri için Velzon'da https://www.velzon.tr/terminal/ sayfasını inceleyin.\","
                + "\"instagramCaption\":\"THYAO analizi burada.\","
                + "\"facebookCaption\":\"THYAO analizi burada.\",\"imagePrompt\":\"p\"}";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        assertThrows(IllegalStateException.class, () -> generator.adapt(briefing));
    }

    @Test
    void adaptThrowsWhenCaptionDoesNotMentionSymbol() {
        FakeLlm llm = new FakeLlm();
        llm.response = "{\"x\":\"THYAO teknik görünümde zayıf. "
                + "Daha fazla veri için Velzon'da https://www.velzon.tr/terminal/ sayfasını inceleyin.\","
                + "\"instagramCaption\":\"Kurumsal akış: BANK OF AMERICA %40.8 alıcı.\","
                + "\"facebookCaption\":\"THYAO analizi burada.\",\"imagePrompt\":\"p\"}";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        assertThrows(IllegalStateException.class, () -> generator.adapt(briefing));
    }

    @Test
    void adaptAcceptsSymbolWithTurkishSuffix() throws Exception {
        // "THYAO'nun" gibi doğal ek almış hâller de sembolü içerir sayılmalı.
        FakeLlm llm = new FakeLlm();
        llm.response = "{\"x\":\"THYAO'nun teknik görünümü zayıf. "
                + "Daha fazla veri için Velzon'da https://www.velzon.tr/terminal/ sayfasını inceleyin.\","
                + "\"instagramCaption\":\"THYAO'da kurumsal akış güçlü.\","
                + "\"facebookCaption\":\"THYAO'daki teknik tablo.\",\"imagePrompt\":\"p\"}";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        assertDoesNotThrow(() -> generator.adapt(briefing));
    }

    @Test
    void adaptTruncatesInstagramCaptionInsteadOfThrowingWhenTooLong() throws Exception {
        // 2026-08-24 canlı bug: Instagram "caption too long" (2200 karakter
        // sert sınır) ile reddetti — X ve Facebook o turda sorunsuzdu, bu
        // yüzden fırlatmak yerine kısaltmalıyız (fırlatmak X/FB'yi de kaçırır).
        FakeLlm llm = new FakeLlm();
        String tooLong = "THYAO analizinde kurumsal akış çok zengin. " + "a".repeat(2300);
        llm.response = "{\"x\":\"THYAO teknik görünümde zayıf. "
                + "Daha fazla veri için Velzon'da https://www.velzon.tr/terminal/ sayfasını inceleyin.\","
                + "\"instagramCaption\":\"" + tooLong + "\","
                + "\"facebookCaption\":\"THYAO analizi burada.\",\"imagePrompt\":\"p\"}";
        var generator = new VelzonAiBriefingPostGenerator(llm);
        var briefing = new VelzonBriefingClient.Briefing("THYAO", "1G", FULL_BRIEFING);

        var adapted = generator.adapt(briefing);

        assertTrue(adapted.instagramCaption().length() <= 2200,
                "kısaltma sonrası hâlâ 2200'ü aşıyor: " + adapted.instagramCaption().length());
        assertTrue(adapted.instagramCaption().contains("THYAO"), "sembol kısaltmada kayboldu");
        assertTrue(adapted.instagramCaption().contains("velzon.tr/terminal"), "CTA linki kayboldu");
    }

    @Test
    void truncateForInstagramLeavesShortCaptionUnchanged() {
        String shortCaption = "THYAO kısa bir analiz.";
        assertEquals(shortCaption, VelzonAiBriefingPostGenerator.truncateForInstagram(shortCaption));
    }

    @Test
    void truncateForInstagramFitsWithinLimitAndKeepsCta() {
        String longCaption = "THYAO analizinde çok fazla detay var. " + "x".repeat(3000);
        String result = VelzonAiBriefingPostGenerator.truncateForInstagram(longCaption);

        assertTrue(result.length() <= 2200, "sonuç 2200'ü aşıyor: " + result.length());
        assertTrue(result.contains("https://www.velzon.tr/terminal/"), "CTA linki yok");
    }
}
