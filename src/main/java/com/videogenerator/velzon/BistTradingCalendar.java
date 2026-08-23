package com.videogenerator.velzon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * BIST işlem takvimi — hafta sonu, resmi tam gün tatiller ve yarım gün
 * (arefe) kapanışları. Scyborsa'nın (kardeş proje, kullanıcının kendi
 * üretim koduyla doğrulanmış) {@code com.scyborsa.api.enums.SessionHolidays}
 * sınıfından uyarlanmıştır — tatil verileri (2025-2027) birebir korunmuştur,
 * sadece takas/teminat-özel metodlar (bu projeyle ilgisiz) çıkarılmıştır.
 *
 * Kullanım amacı: Velzon AI Brifing zamanlanmış işinin sadece BIST açıkken
 * (hafta içi, tatil olmayan, yarım günlerde kapanıştan önce) çalışmasını
 * sağlamak — kapalıyken tetiklenip Django'ya boşuna istek atmasın.
 */
public final class BistTradingCalendar {
    private BistTradingCalendar() {
    }

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * BIST Pay Piyasası normal seans saatleri (öğle arası yok, 2016'dan beri
     * tek seans). Kaynak Scyborsa'nın {@code SessionHolidays} sınıfı bu
     * sınırları taşımıyordu (muhtemelen o projede ayrı tutuluyor) — bu
     * projenin kendi ihtiyacı için burada eklendi.
     */
    private static final LocalTime SESSION_OPEN = LocalTime.of(10, 0);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(18, 0);

    private enum Holiday {
        // ==================== 2025 ====================
        D_20250328("20250328", true, LocalTime.of(12, 30)),
        D_20250330("20250330", false, null),
        D_20250331("20250331", false, null),
        D_20250423("20250423", false, null),
        D_20250501("20250501", false, null),
        D_20250519("20250519", false, null),
        D_20250605("20250605", true, LocalTime.of(12, 30)),
        D_20250606("20250606", false, null),
        D_20250607("20250607", false, null),
        D_20250608("20250608", false, null),
        D_20250609("20250609", false, null),
        D_20250715("20250715", false, null),
        D_20251028("20251028", true, LocalTime.of(12, 30)),
        D_20251029("20251029", false, null),

        // ==================== 2026 ====================
        D_20260101("20260101", false, null),
        D_20260319("20260319", true, LocalTime.of(12, 30)),
        D_20260320("20260320", false, null),
        D_20260423("20260423", false, null),
        D_20260501("20260501", false, null),
        D_20260519("20260519", false, null),
        D_20260526("20260526", true, LocalTime.of(12, 30)),
        D_20260527("20260527", false, null),
        D_20260528("20260528", false, null),
        D_20260529("20260529", false, null),
        D_20260715("20260715", false, null),
        D_20261028("20261028", true, LocalTime.of(12, 30)),
        D_20261029("20261029", false, null),

        // ==================== 2027 ====================
        D_20270101("20270101", false, null),
        D_20270308("20270308", true, LocalTime.of(12, 30)),
        D_20270309("20270309", false, null),
        D_20270310("20270310", false, null),
        D_20270311("20270311", false, null),
        D_20270423("20270423", false, null),
        D_20270517("20270517", false, null),
        D_20270518("20270518", false, null),
        D_20270519("20270519", false, null),
        D_20270715("20270715", false, null),
        D_20270830("20270830", false, null),
        D_20271028("20271028", true, LocalTime.of(12, 30)),
        D_20271029("20271029", false, null);

        final String dateStr;
        final boolean halfDay;
        final LocalTime closingTime;

        Holiday(String dateStr, boolean halfDay, LocalTime closingTime) {
            this.dateStr = dateStr;
            this.halfDay = halfDay;
            this.closingTime = closingTime;
        }
    }

    private static Holiday findByDate(LocalDate date) {
        String formatted = date.format(DATE_FORMAT);
        for (Holiday h : Holiday.values()) {
            if (h.dateStr.equals(formatted)) {
                return h;
            }
        }
        return null;
    }

    /** Tam gün resmi tatil mi (yarım günler burada false döner). */
    public static boolean isHoliday(LocalDate date) {
        Holiday h = findByDate(date);
        return h != null && !h.halfDay;
    }

    /** Yarım gün (arefe) tatili mi. */
    public static boolean isHalfDay(LocalDate date) {
        Holiday h = findByDate(date);
        return h != null && h.halfDay;
    }

    /** Yarım günlerde kapanış saati; yarım gün değilse null. */
    public static LocalTime getHalfDayClosingTime(LocalDate date) {
        Holiday h = findByDate(date);
        return (h != null && h.halfDay) ? h.closingTime : null;
    }

    /** Hafta sonu veya tam gün tatil — yarım günler işlem günü sayılır. */
    public static boolean isNonTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }
        return isHoliday(date);
    }

    /**
     * Verilen tarih+saat işlem saati içinde mi — hafta sonu, tam gün tatil,
     * normal seans aralığı (10:00-18:00) dışı ve yarım günlerde kapanış
     * saatinden sonrası hariç.
     */
    public static boolean isTradingTime(LocalDate date, LocalTime time) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        if (isHoliday(date)) {
            return false;
        }
        if (time.isBefore(SESSION_OPEN)) {
            return false;
        }
        LocalTime closing = getHalfDayClosingTime(date);
        LocalTime effectiveClose = closing != null ? closing : SESSION_CLOSE;
        return time.isBefore(effectiveClose);
    }

    /** Şu an (İstanbul saati) işlem saati içinde mi. */
    public static boolean isTradingTimeNow() {
        return isTradingTime(LocalDate.now(ISTANBUL_ZONE), LocalTime.now(ISTANBUL_ZONE));
    }

    /** Bugün (İstanbul saati) işlem günü mü. */
    public static boolean isTradingDay() {
        return !isNonTradingDay(LocalDate.now(ISTANBUL_ZONE));
    }

    /** Verilen tarihten önceki en yakın işlem gününü bulur (hafta sonu/tam-gün-tatil atlanır). */
    public static LocalDate getPreviousTradingDay(LocalDate date) {
        LocalDate d = date.minusDays(1);
        while (isNonTradingDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** Verilen tarihten sonraki en yakın işlem gününü bulur. */
    public static LocalDate getNextTradingDay(LocalDate date) {
        LocalDate d = date.plusDays(1);
        while (isNonTradingDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }
}
