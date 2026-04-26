package com.amazonpricemonitor.service.ai;

import java.math.BigDecimal;

/**
 * Deterministic, pre-computed view of a product's last-N-days price activity.
 * <p>
 * This is the *only* numeric source of truth handed to the LLM — the LLM is
 * instructed to narrate these values, never to compute or invent its own.
 *
 * @param oldestPrice    First successful price in the window.
 * @param latestPrice    Most recent successful price in the window.
 * @param minPrice       Minimum successful price observed.
 * @param maxPrice       Maximum successful price observed.
 * @param avgPrice       Arithmetic mean of successful prices, scale 2.
 * @param pctChange      ((latest - oldest) / oldest) * 100, scale 2. May be negative.
 * @param absChange      latest - oldest, scale 2. May be negative.
 * @param numChecks      Total checks in the window (success + failure).
 * @param numSuccess     Successful checks in the window.
 * @param numFailures    Failed checks in the window.
 * @param currency       Currency code of the most recent successful check (e.g. "USD").
 * @param windowDays     Number of days in the trailing window (e.g. 7).
 */
public record PriceTrendStats(
        BigDecimal oldestPrice,
        BigDecimal latestPrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal avgPrice,
        BigDecimal pctChange,
        BigDecimal absChange,
        int numChecks,
        int numSuccess,
        int numFailures,
        String currency,
        int windowDays) {

    public boolean hasEnoughDataToNarrate() {
        return numSuccess >= 2;
    }
}
