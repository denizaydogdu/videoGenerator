package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class BistTradingCalendarTest {

    @Test
    void weekdayDuringSessionIsTradingTime() {
        // 2026-08-24 Pazartesi, tatil değil, 14:00
        LocalDate mon = LocalDate.of(2026, 8, 24);
        assertEquals(DayOfWeek.MONDAY, mon.getDayOfWeek());
        assertTrue(BistTradingCalendar.isTradingTime(mon, LocalTime.of(14, 0)));
    }

    @Test
    void beforeOpenIsNotTradingTime() {
        LocalDate mon = LocalDate.of(2026, 8, 24);
        assertFalse(BistTradingCalendar.isTradingTime(mon, LocalTime.of(9, 30)));
    }

    @Test
    void afterCloseIsNotTradingTime() {
        LocalDate mon = LocalDate.of(2026, 8, 24);
        assertFalse(BistTradingCalendar.isTradingTime(mon, LocalTime.of(18, 30)));
    }

    @Test
    void weekendIsNotTradingTime() {
        // 2026-08-22 Cumartesi
        LocalDate sat = LocalDate.of(2026, 8, 22);
        assertEquals(DayOfWeek.SATURDAY, sat.getDayOfWeek());
        assertFalse(BistTradingCalendar.isTradingTime(sat, LocalTime.of(14, 0)));
        assertTrue(BistTradingCalendar.isNonTradingDay(sat));
    }

    @Test
    void fullDayHolidayIsNotTradingTime() {
        // 23 Nisan 2026 — Ulusal Egemenlik ve Çocuk Bayramı, tam gün tatil
        LocalDate holiday = LocalDate.of(2026, 4, 23);
        assertTrue(BistTradingCalendar.isHoliday(holiday));
        assertFalse(BistTradingCalendar.isTradingTime(holiday, LocalTime.of(12, 0)));
    }

    @Test
    void halfDayAllowsTradingBeforeClosingTime() {
        // 2026-05-26 — arefe (yarım gün, kapanış 12:30)
        LocalDate halfDay = LocalDate.of(2026, 5, 26);
        assertTrue(BistTradingCalendar.isHalfDay(halfDay));
        assertEquals(LocalTime.of(12, 30), BistTradingCalendar.getHalfDayClosingTime(halfDay));
        assertTrue(BistTradingCalendar.isTradingTime(halfDay, LocalTime.of(12, 0)));
    }

    @Test
    void halfDayBlocksTradingAtOrAfterClosingTime() {
        LocalDate halfDay = LocalDate.of(2026, 5, 26);
        assertFalse(BistTradingCalendar.isTradingTime(halfDay, LocalTime.of(12, 30)));
        assertFalse(BistTradingCalendar.isTradingTime(halfDay, LocalTime.of(15, 0)));
    }

    @Test
    void halfDayIsStillATradingDayNotNonTradingDay() {
        // Yarım gün işlem günüdür, isNonTradingDay bunu tatil saymamalı
        LocalDate halfDay = LocalDate.of(2026, 5, 26);
        assertFalse(BistTradingCalendar.isNonTradingDay(halfDay));
    }

    @Test
    void ordinaryWeekdayIsNotHolidayOrHalfDay() {
        LocalDate ordinary = LocalDate.of(2026, 8, 24);
        assertFalse(BistTradingCalendar.isHoliday(ordinary));
        assertFalse(BistTradingCalendar.isHalfDay(ordinary));
        assertNull(BistTradingCalendar.getHalfDayClosingTime(ordinary));
    }

    @Test
    void getPreviousTradingDaySkipsWeekendAndHoliday() {
        // 2026-04-23 (Perşembe, tam gün tatil) öncesi işlem günü aranırsa
        // 2026-04-22 (Çarşamba) dönmeli
        LocalDate day = LocalDate.of(2026, 4, 23);
        assertEquals(LocalDate.of(2026, 4, 22), BistTradingCalendar.getPreviousTradingDay(day));
    }

    @Test
    void getNextTradingDaySkipsWeekend() {
        // 2026-08-22 Cumartesi'den sonraki işlem günü 2026-08-24 Pazartesi olmalı
        LocalDate sat = LocalDate.of(2026, 8, 22);
        assertEquals(LocalDate.of(2026, 8, 24), BistTradingCalendar.getNextTradingDay(sat));
    }
}
