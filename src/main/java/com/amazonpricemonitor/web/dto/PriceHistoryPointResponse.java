package com.amazonpricemonitor.web.dto;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.PriceCheck;
import java.math.BigDecimal;
import java.time.Instant;

public record PriceHistoryPointResponse(
        Instant checkedAt, BigDecimal price, FetchMethod method, boolean success, String errorMessage) {

    public static PriceHistoryPointResponse fromEntity(PriceCheck row) {
        return new PriceHistoryPointResponse(
                row.getCreatedAt(),
                row.getPriceAmount(),
                row.getFetchMethod(),
                row.isSuccess(),
                row.getErrorMessage());
    }
}
