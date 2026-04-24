package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.FetchMethod;
import java.math.BigDecimal;
import java.util.Optional;

public record PriceQuote(BigDecimal amount, String currency, FetchMethod method) {

    public static Optional<PriceQuote> empty() {
        return Optional.empty();
    }
}
