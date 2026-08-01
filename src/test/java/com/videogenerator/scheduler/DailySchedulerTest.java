package com.videogenerator.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DailySchedulerTest {
    @Test
    void executeNowRunsInjectedTask() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        DailyScheduler scheduler = new DailyScheduler(ran::countDown);
        scheduler.executeNow();
        assertTrue(ran.await(5, TimeUnit.SECONDS), "task should run");
        scheduler.stop();
    }

    @Test
    void taskFailureDoesNotKillScheduler() throws Exception {
        CountDownLatch second = new CountDownLatch(2);
        DailyScheduler scheduler = new DailyScheduler(() -> {
            second.countDown();
            throw new RuntimeException("boom");
        });
        scheduler.executeNow();
        scheduler.executeNow();
        assertTrue(second.await(5, TimeUnit.SECONDS), "both runs should happen");
        scheduler.stop();
    }
}
