package com.videogenerator.velzon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link VelzonAiBriefingJob}'ı BIST seans saatleri içinde günde 5 sabit
 * saatte tetikler (10:30/12:00/13:30/15:00/16:30, İstanbul saati) —
 * {@code DailyScheduler}'ın tek-saatlik cron deseninden farklı olarak
 * birden fazla bağımsız {@code scheduleAtFixedRate} kaydı kullanır.
 *
 * Seans dışı/tatil kontrolü burada değil, {@link VelzonAiBriefingJob#run()}
 * içindeki {@code tradingTimeCheck}'te yapılır — bu sınıf sadece "saatte bir
 * kere dene" sorumluluğunu taşır.
 */
public class VelzonAiBriefingScheduler {
    private static final Logger logger = LoggerFactory.getLogger(VelzonAiBriefingScheduler.class);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private static final List<LocalTime> TRIGGER_TIMES = List.of(
            LocalTime.of(10, 30),
            LocalTime.of(12, 0),
            LocalTime.of(13, 30),
            LocalTime.of(15, 0),
            LocalTime.of(16, 30));

    private final Runnable task;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public VelzonAiBriefingScheduler(Runnable task) {
        this.task = task;
    }

    /** 5 tetikleme saatinin her biri için 24 saatte bir tekrar eden ayrı bir kayıt kurar. */
    public void start() {
        ZonedDateTime now = ZonedDateTime.now(ISTANBUL);
        for (LocalTime triggerTime : TRIGGER_TIMES) {
            long initialDelay = calculateInitialDelay(now, triggerTime);
            executor.scheduleAtFixedRate(this::executeTask, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
            logger.info("Velzon AI brifing tetikleyicisi kuruldu: {} (ilk çalıştırma {} saniye sonra)",
                    triggerTime, initialDelay);
        }
    }

    static long calculateInitialDelay(ZonedDateTime now, LocalTime target) {
        ZonedDateTime next = now.withHour(target.getHour()).withMinute(target.getMinute())
                .withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next).getSeconds();
    }

    private void executeTask() {
        try {
            task.run();
        } catch (Exception e) {
            logger.error("Velzon AI brifing zamanlayıcı tetiklemesi başarısız", e);
        }
    }

    /** Testte manuel tetikleme için. */
    public void executeNow() {
        executeTask();
    }

    public void stop() {
        executor.shutdownNow();
    }
}
