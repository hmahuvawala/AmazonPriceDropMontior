package com.amazonpricemonitor.scheduler;

import com.amazonpricemonitor.config.SchedulerProperties;
import com.amazonpricemonitor.service.PriceMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PriceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);

    private final PriceMonitoringService priceMonitoringService;

    public PriceCheckScheduler(PriceMonitoringService priceMonitoringService) {
        this.priceMonitoringService = priceMonitoringService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.interval-ms:3600000}", initialDelayString = "60000")
    public void runScheduledChecks() {
        log.info("Starting scheduled Amazon price checks");
        priceMonitoringService.runChecksForActiveProducts();
        log.info("Finished scheduled Amazon price checks");
    }
}
