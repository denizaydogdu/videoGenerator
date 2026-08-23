package com.videogenerator.velzon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BIST 100 endeksinin (XU100) sembol havuzu — Velzon AI Brifing içerik
 * job'unun günlük "5 farklı hisse" seçimi için kaynak liste. Kullanıcının
 * paylaştığı canlı BIST 100 fiyat tablosundan (2026-08-23) alınmıştır.
 *
 * Sabit bir anlık görüntü — endeks bileşimi çeyreklik dönemlerde
 * (Ocak-Mart/Nisan-Haziran/Temmuz-Eylül/Ekim-Aralık) değişebilir, bu liste
 * o zaman elle güncellenmeli.
 */
public final class VelzonBist100SymbolPool {
    private VelzonBist100SymbolPool() {
    }

    public static final List<String> SYMBOLS = List.of(
            "AEFES", "AGHOL", "AGROT", "AHGAZ", "AKBNK", "AKSA", "AKSEN", "ALARK", "ALFAS", "ALTNY",
            "ANHYT", "ANSGR", "ARCLK", "ARDYZ", "ASELS", "ASTOR", "AVPGY", "BERA", "BIMAS", "BRSAN",
            "BRYAT", "BSOKE", "BTCIM", "CANTE", "CCOLA", "CIMSA", "CLEBI", "CWENE", "DOAS", "DOHOL",
            "ECILC", "EFOR", "EGEEN", "EKGYO", "ENERY", "ENJSA", "ENKAI", "EREGL", "EUPWR", "FROTO",
            "GARAN", "GESAN", "GOLTS", "GRTHO", "GSRAY", "GUBRF", "HALKB", "HEKTS", "IEYHO", "ISCTR",
            "ISMEN", "KARSN", "KCAER", "KCHOL", "KONTR", "KONYA", "KRDMD", "KTLEV", "LMKDC", "MAGEN",
            "MAVI", "MGROS", "MIATK", "MPARK", "OBAMS", "ODAS", "OTKAR", "OYAKC", "PAHOL", "PASEU",
            "PETKM", "PGSUS", "RALYH", "REEDR", "RYGYO", "SAHOL", "SASA", "SELEC", "SISE", "SKBNK",
            "SMRTG", "SOKM", "TABGD", "TAVHL", "TCELL", "THYAO", "TKFEN", "TOASO", "TRALT", "TRENJ",
            "TRMET", "TSKB", "TTKOM", "TTRAK", "TUPRS", "TURSG", "ULKER", "VAKBN", "VESTL", "YEOTK",
            "YKBNK", "ZOREN");

    /** Havuzdan rastgele, tekrarsız {@code count} sembol seçer. */
    public static List<String> pickRandom(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        if (count > SYMBOLS.size()) {
            throw new IllegalArgumentException(
                    "count (" + count + ") exceeds pool size (" + SYMBOLS.size() + ")");
        }
        List<String> shuffled = new ArrayList<>(SYMBOLS);
        Collections.shuffle(shuffled);
        return List.copyOf(shuffled.subList(0, count));
    }
}
