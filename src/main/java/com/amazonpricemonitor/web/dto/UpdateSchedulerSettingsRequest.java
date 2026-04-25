package com.amazonpricemonitor.web.dto;

import com.amazonpricemonitor.service.SchedulerSettingsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSchedulerSettingsRequest(
        @NotNull
                @Min(SchedulerSettingsService.MIN_CHECK_INTERVAL_MS)
                @Max(SchedulerSettingsService.MAX_CHECK_INTERVAL_MS)
                Long checkIntervalMs) {}
