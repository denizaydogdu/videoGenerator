package com.videogenerator.processor;

import com.videogenerator.model.AudioMixConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ducking filtre grafı regresyonu — canlı hata (2026-08-02):
 * "Filter 'asplit' has output 0 (music) unconnected". Eski graf ayrıca
 * sidechain'i TERS bağlıyordu (ses kısılıyordu, müzik değil) ve [voice]
 * etiketini iki kez tüketiyordu.
 */
class MixFilterTest {

    private AudioMixConfig ducking() {
        AudioMixConfig c = new AudioMixConfig();
        c.setVoiceoverVolume(1.0);
        c.setMusicVolume(0.25);
        c.setDuckingEnabled(true);
        c.setNormalizeAudio(true);
        return c;
    }

    @Test
    void duckingGraphIsValidAndCorrectlyOriented() {
        String f = FFmpegWrapper.buildMixFilter(ducking(), 60.0);
        // Bölünen VOICE olmalı (hem tetikleyici hem mikse girecek)
        assertTrue(f.contains("[voice]asplit[voice_mix][voice_sc]"), f);
        // sidechaincompress: 1. giriş = kısılacak (MÜZİK), 2. giriş = tetik (SES)
        assertTrue(f.contains("[music_vol][voice_sc]sidechaincompress"), f);
        // Mikse ses kopyası + kısılmış müzik girer
        assertTrue(f.contains("[voice_mix][music_ducked]amix"), f);
        // Eski hatalı etiketler yok
        assertFalse(f.contains("asplit[music]"), "kullanılmayan [music] çıkışı olmamalı");
        assertFalse(f.contains("[voice][sc]sidechaincompress"), "ters sidechain olmamalı");
    }

    @Test
    void nonDuckingGraphStillSimpleMix() {
        AudioMixConfig c = ducking();
        c.setDuckingEnabled(false);
        String f = FFmpegWrapper.buildMixFilter(c, 60.0);
        assertTrue(f.contains("[voice][music_vol]amix"), f);
        assertFalse(f.contains("sidechaincompress"));
    }

    @Test
    void everyDefinedLabelIsConsumed() {
        // Genel sözleşme: tanımlanan hiçbir etiket bağlantısız kalmamalı
        String f = FFmpegWrapper.buildMixFilter(ducking(), 60.0);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\[([a-z_]+)\\](?=;|$)").matcher(f);
        while (m.find()) {
            String label = m.group(1);
            // çıkış etiketi hariç: sonrasında giriş olarak geçmeli
            int defEnd = m.end();
            if (defEnd < f.length()) {
                assertTrue(f.indexOf("[" + label + "]", defEnd) >= 0,
                        "etiket tanımlandı ama tüketilmedi: " + label + "\n" + f);
            }
        }
    }
}
