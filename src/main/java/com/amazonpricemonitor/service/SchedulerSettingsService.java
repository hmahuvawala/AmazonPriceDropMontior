package com.amazonpricemonitor.service;

import com.amazonpricemonitor.config.SchedulerProperties;
import com.amazonpricemonitor.domain.SchedulerSettings;
import com.amazonpricemonitor.repository.SchedulerSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerSettingsService {

    public static final long MIN_CHECK_INTERVAL_MS = 60_000L;
    public static final long MAX_CHECK_INTERVAL_MS = 604_800_000L;

    private final SchedulerSettingsRepository repository;
    private final SchedulerProperties schedulerProperties;

    public SchedulerSettingsService(
            SchedulerSettingsRepository repository, SchedulerProperties schedulerProperties) {
        this.repository = repository;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Ensures row {@code id=1} exists (for H2 tests without Flyway). Production DB is seeded by Flyway V3.
     */
    @Transactional
    public void ensureDefaultRowIfMissing() {
        if (repository.findById((short) 1).isEmpty()) {
            SchedulerSettings row = new SchedulerSettings();
            row.setId((short) 1);
            row.setCheckIntervalMs(clampToRange(schedulerProperties.getIntervalMs()));
            repository.save(row);
        }
    }

    @Transactional(readOnly = true)
    public long getCheckIntervalMs() {
        return repository
                .findById((short) 1)
                .map(SchedulerSettings::getCheckIntervalMs)
                .orElseThrow(() -> new IllegalStateException("scheduler_settings row missing; run Flyway or ensureDefaultRowIfMissing"));
    }

    @Transactional
    public void updateCheckIntervalMs(long checkIntervalMs) {
        long clamped = clampToRange(checkIntervalMs);
        SchedulerSettings row = repository
                .findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("scheduler_settings row missing"));
        row.setCheckIntervalMs(clamped);
        repository.save(row);
    }

    private static long clampToRange(long ms) {
        return Math.min(MAX_CHECK_INTERVAL_MS, Math.max(MIN_CHECK_INTERVAL_MS, ms));
    }
}
