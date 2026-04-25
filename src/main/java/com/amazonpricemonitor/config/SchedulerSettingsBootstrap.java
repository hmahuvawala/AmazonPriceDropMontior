package com.amazonpricemonitor.config;

import com.amazonpricemonitor.service.SchedulerSettingsService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class SchedulerSettingsBootstrap implements ApplicationRunner {

    private final SchedulerSettingsService schedulerSettingsService;

    SchedulerSettingsBootstrap(SchedulerSettingsService schedulerSettingsService) {
        this.schedulerSettingsService = schedulerSettingsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        schedulerSettingsService.ensureDefaultRowIfMissing();
    }
}
