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
 * Verilen {@link Runnable}'ı günde N sabit saatte tetikler — birden fazla
 * bağımsız {@code scheduleAtFixedRate} kaydı kullanır ({@code DailyScheduler}'ın
 * tek-saatlik cron deseninden farklı). Varsayılan (parametresiz) constructor
 * mevcut Velzon AI Brifing (rastgele BIST100 hissesi) işi için BIST seans
 * saatleri içindeki 5 sabit saati kullanır (10:30/12:00/13:30/15:00/16:30,
 * İstanbul saati); {@link #VelzonAiBriefingScheduler(Runnable, List)}
 * constructor'ı 2026-08-24'te eklendi — XU100 günlük özet işleri gibi
 * tek-saatlik (09:30 sabah / 19:00 akşam) ayrı zamanlayıcı ihtiyacı için.
 *
 * Seans/gün-tatil kontrolü burada değil, ilgili {@link VelzonAiBriefingJob#run()}
 * içindeki {@code tradingTimeCheck}'te yapılır (job'a göre "seans açık mı"
 * veya "bugün işlem günü mü" olabilir) — bu sınıf sadece "saatte bir kere
 * dene" sorumluluğunu taşır.
 */
public class VelzonAiBriefingScheduler {
    private static final Logger logger = LoggerFactory.getLogger(VelzonAiBriefingScheduler.class);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private static final List<LocalTime> DEFAULT_TRIGGER_TIMES = List.of(
            LocalTime.of(10, 30),
            LocalTime.of(12, 0),
            LocalTime.of(13, 30),
            LocalTime.of(15, 0),
            LocalTime.of(16, 30));

    private final Runnable task;
    private final List<LocalTime> triggerTimes;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public VelzonAiBriefingScheduler(Runnable task) {
        this(task, DEFAULT_TRIGGER_TIMES);
    }

    public VelzonAiBriefingScheduler(Runnable task, List<LocalTime> triggerTimes) {
        this.task = task;
        this.triggerTimes = triggerTimes;
    }

    /** Testte/loglamada hangi saatlerin kurulu olduğunu doğrulamak için. */
    List<LocalTime> triggerTimes() {
        return triggerTimes;
    }

    /** Tetikleme saatinin her biri için 24 saatte bir tekrar eden ayrı bir kayıt kurar. */
    public void start() {
        ZonedDateTime now = ZonedDateTime.now(ISTANBUL);
        for (LocalTime triggerTime : triggerTimes) {
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
