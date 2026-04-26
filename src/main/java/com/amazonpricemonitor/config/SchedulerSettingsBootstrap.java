package com.amazonpricemonitor.config;

import com.amazonpricemonitor.service.NotificationRecipientsService;
import com.amazonpricemonitor.service.SchedulerSettingsService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class SchedulerSettingsBootstrap implements ApplicationRunner {

    private final SchedulerSettingsService schedulerSettingsService;
    private final NotificationRecipientsService notificationRecipientsService;

    SchedulerSettingsBootstrap(
            SchedulerSettingsService schedulerSettingsService,
            NotificationRecipientsService notificationRecipientsService) {
        this.schedulerSettingsService = schedulerSettingsService;
        this.notificationRecipientsService = notificationRecipientsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        schedulerSettingsService.ensureDefaultRowIfMissing();
        notificationRecipientsService.ensureDefaultRowIfMissing();
    }
}
