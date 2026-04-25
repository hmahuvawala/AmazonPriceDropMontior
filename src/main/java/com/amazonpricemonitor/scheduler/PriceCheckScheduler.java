package com.amazonpricemonitor.scheduler;

import com.amazonpricemonitor.service.PriceMonitoringService;
import com.amazonpricemonitor.service.SchedulerSettingsService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PriceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);

    /** Delay after startup before the first scheduled run (not configurable in the UI). */
    static final long BOOT_INITIAL_DELAY_MS = 60_000L;

    private final ThreadPoolTaskScheduler taskScheduler;
    private final PriceMonitoringService priceMonitoringService;
    private final SchedulerSettingsService schedulerSettingsService;

    private final Object scheduleLock = new Object();
    private volatile ScheduledFuture<?> nextRun;
    private volatile boolean stopped;
    private volatile boolean chainStarted;

    public PriceCheckScheduler(
            @Qualifier("priceCheckTaskScheduler") ThreadPoolTaskScheduler taskScheduler,
            PriceMonitoringService priceMonitoringService,
            SchedulerSettingsService schedulerSettingsService) {
        this.taskScheduler = taskScheduler;
        this.priceMonitoringService = priceMonitoringService;
        this.schedulerSettingsService = schedulerSettingsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        synchronized (scheduleLock) {
            if (chainStarted) {
                return;
            }
            chainStarted = true;
            stopped = false;
        }
        scheduleNext(BOOT_INITIAL_DELAY_MS);
    }

    @PreDestroy
    public void onShutdown() {
        stopped = true;
        synchronized (scheduleLock) {
            if (nextRun != null) {
                nextRun.cancel(false);
            }
        }
    }

    private void scheduleNext(long delayMs) {
        synchronized (scheduleLock) {
            if (stopped) {
                return;
            }
            if (nextRun != null) {
                nextRun.cancel(false);
            }
            nextRun = taskScheduler.schedule(this::runChecksAndScheduleFollowing, Instant.now().plusMillis(delayMs));
        }
    }

    private void runChecksAndScheduleFollowing() {
        try {
            log.info("Starting scheduled Amazon price checks");
            priceMonitoringService.runChecksForActiveProducts();
            log.info("Finished scheduled Amazon price checks");
        } catch (Exception ex) {
            log.error("Scheduled Amazon price checks failed", ex);
        } finally {
            if (!stopped) {
                long nextDelay = schedulerSettingsService.getCheckIntervalMs();
                scheduleNext(nextDelay);
            }
        }
    }
}
