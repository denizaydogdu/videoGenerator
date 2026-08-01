package com.videogenerator.scheduler;

import com.videogenerator.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for automatic daily video generation. Task-agnostic: the
 * injected Runnable decides WHAT runs (shorts-factory pipeline per
 * channel); this class only decides WHEN.
 */
public class DailyScheduler {
    private static final Logger logger = LoggerFactory.getLogger(DailyScheduler.class);
    private final Configuration config;
    private final Runnable task;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public DailyScheduler(Runnable task) {
        this.config = Configuration.getInstance();
        this.task = task;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * Starts the scheduler
     */
    public void start() {
        if (running) {
            logger.warn("Scheduler is already running");
            return;
        }

        logger.info("Starting daily scheduler");
        String cronExpression = config.getSchedulerCron();
        logger.info("Schedule: {}", cronExpression);

        // Parse cron expression (simple format: "minute hour * * *")
        String[] parts = cronExpression.split(" ");
        if (parts.length < 2) {
            logger.error("Invalid cron expression: {}", cronExpression);
            return;
        }

        int targetMinute = Integer.parseInt(parts[0]);
        int targetHour = Integer.parseInt(parts[1]);

        // Calculate initial delay
        long initialDelay = calculateInitialDelay(targetHour, targetMinute);

        logger.info("Next execution in {} minutes", initialDelay / 60);
        logger.info("Will run daily at {:02d}:{:02d}", targetHour, targetMinute);

        // Schedule daily execution
        scheduler.scheduleAtFixedRate(
                this::executeJob,
                initialDelay,
                24 * 60 * 60, // 24 hours in seconds
                TimeUnit.SECONDS
        );

        running = true;
        logger.info("Scheduler started successfully");
    }

    /**
     * Calculates seconds until next execution time
     */
    private long calculateInitialDelay(int targetHour, int targetMinute) {
        ZoneId timezone = ZoneId.of(config.getSchedulerTimezone());
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime nextRun = now
                .withHour(targetHour)
                .withMinute(targetMinute)
                .withSecond(0)
                .withNano(0);

        // If the time has already passed today, schedule for tomorrow
        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusDays(1);
        }

        long seconds = Duration.between(now, nextRun).getSeconds();
        logger.info("Next execution scheduled for: {}",
                nextRun.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));

        return seconds;
    }

    /**
     * Executes the scheduled job
     */
    private void executeJob() {
        logger.info("======================================");
        logger.info("Scheduled job triggered");
        logger.info("======================================");

        try {
            task.run();
            logger.info("Scheduled task completed");
        } catch (Exception e) {
            logger.error("Scheduled task failed", e);
            // Continue running even if one execution fails
        }

        logger.info("Next execution in 24 hours");
    }

    /**
     * Executes job immediately (for manual trigger)
     */
    public void executeNow() {
        logger.info("Manual execution triggered");
        scheduler.submit(this::executeJob);
    }

    /**
     * Stops the scheduler
     */
    public void stop() {
        // Executor daima kapatılır: executeNow() start() olmadan da iş
        // gönderebilir; erken dönüş thread sızdırırdı
        logger.info("Stopping scheduler...");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.error("Scheduler did not terminate");
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        running = false;
        logger.info("Scheduler stopped");
    }

    /**
     * Checks if scheduler is running
     */
    public boolean isRunning() {
        return running && !scheduler.isShutdown();
    }

    /**
     * Gets time until next execution in seconds
     */
    public long getTimeUntilNextExecution() {
        String cronExpression = config.getSchedulerCron();
        String[] parts = cronExpression.split(" ");

        if (parts.length < 2) {
            return -1;
        }

        int targetMinute = Integer.parseInt(parts[0]);
        int targetHour = Integer.parseInt(parts[1]);

        return calculateInitialDelay(targetHour, targetMinute);
    }
}
