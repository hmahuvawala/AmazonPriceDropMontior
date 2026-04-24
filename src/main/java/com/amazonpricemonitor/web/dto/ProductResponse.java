package com.amazonpricemonitor.web.dto;

import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String amazonUrl,
        String displayName,
        BigDecimal thresholdPct,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse fromEntity(MonitoredProduct entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getAmazonUrl(),
                entity.getDisplayName(),
                entity.getThresholdPct(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
