package com.amazonpricemonitor.web;

import com.amazonpricemonitor.service.PriceMonitoringService;
import com.amazonpricemonitor.service.SchedulerSettingsService;
import com.amazonpricemonitor.web.dto.SchedulerSettingsResponse;
import com.amazonpricemonitor.web.dto.UpdateSchedulerSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PriceMonitoringService priceMonitoringService;
    private final SchedulerSettingsService schedulerSettingsService;

    public AdminController(
            PriceMonitoringService priceMonitoringService, SchedulerSettingsService schedulerSettingsService) {
        this.priceMonitoringService = priceMonitoringService;
        this.schedulerSettingsService = schedulerSettingsService;
    }

    @PostMapping("/run-checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runChecksNow() {
        priceMonitoringService.runChecksForActiveProducts();
    }

    @GetMapping("/scheduler-settings")
    public SchedulerSettingsResponse getSchedulerSettings() {
        return new SchedulerSettingsResponse(schedulerSettingsService.getCheckIntervalMs());
    }

    @PutMapping("/scheduler-settings")
    public SchedulerSettingsResponse putSchedulerSettings(@Valid @RequestBody UpdateSchedulerSettingsRequest body) {
        schedulerSettingsService.updateCheckIntervalMs(body.checkIntervalMs());
        return new SchedulerSettingsResponse(schedulerSettingsService.getCheckIntervalMs());
    }
}
