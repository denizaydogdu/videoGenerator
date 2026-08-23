package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VelzonAiBriefingSchedulerTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Test
    void calculateInitialDelayForLaterTimeTodayIsPositiveAndSameDay() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 24, 9, 0, 0, 0, ISTANBUL);
        long delaySeconds = VelzonAiBriefingScheduler.calculateInitialDelay(now, LocalTime.of(10, 30));

        assertEquals(90 * 60, delaySeconds); // 9:00 -> 10:30 = 90 dk
    }

    @Test
    void calculateInitialDelayForPastTimeTodayRollsToTomorrow() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 24, 17, 0, 0, 0, ISTANBUL);
        long delaySeconds = VelzonAiBriefingScheduler.calculateInitialDelay(now, LocalTime.of(10, 30));

        // 17:00 bugün -> 10:30 yarın = 17.5 saat
        assertEquals((long) (17.5 * 3600), delaySeconds);
    }

    @Test
    void calculateInitialDelayExactlyAtTargetRollsToTomorrow() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 24, 10, 30, 0, 0, ISTANBUL);
        long delaySeconds = VelzonAiBriefingScheduler.calculateInitialDelay(now, LocalTime.of(10, 30));

        assertEquals(24 * 3600, delaySeconds);
    }

    @Test
    void executeNowRunsInjectedTask() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        VelzonAiBriefingScheduler scheduler = new VelzonAiBriefingScheduler(ran::countDown);
        scheduler.executeNow();
        assertTrue(ran.await(5, TimeUnit.SECONDS), "task should run");
        scheduler.stop();
    }

    @Test
    void taskFailureDoesNotPropagate() {
        VelzonAiBriefingScheduler scheduler = new VelzonAiBriefingScheduler(() -> {
            throw new RuntimeException("boom");
        });
        assertDoesNotThrow(scheduler::executeNow);
        scheduler.stop();
    }
}
