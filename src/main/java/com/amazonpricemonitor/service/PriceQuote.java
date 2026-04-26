package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.FetchMethod;
import java.math.BigDecimal;

public record PriceQuote(BigDecimal amount, String currency, FetchMethod method) {}
