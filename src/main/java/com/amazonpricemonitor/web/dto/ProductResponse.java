package com.amazonpricemonitor.web.dto;

import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String amazonUrl,
        String displayName,
        BigDecimal thresholdPct,
        BigDecimal thresholdAmount,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal lastPrice,
        String lastPriceCurrency) {

    public static ProductResponse fromEntity(MonitoredProduct entity) {
        return fromEntity(entity, null, null);
    }

    public static ProductResponse fromEntity(
            MonitoredProduct entity, BigDecimal lastPrice, String lastPriceCurrency) {
        return new ProductResponse(
                entity.getId(),
                entity.getAmazonUrl(),
                entity.getDisplayName(),
                entity.getThresholdPct(),
                entity.getThresholdAmount(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                lastPrice,
                lastPriceCurrency);
    }
}
