package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;

public interface Notifier {

    /**
     * @param aiSummary optional human-readable narrative of the last 7 days
     *     (already either Gemini-generated or a deterministic fallback). May be null
     *     or blank, in which case the notifier should omit it.
     */
    void notifyPriceDrop(
            MonitoredProduct product,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal dropPercent,
            BigDecimal dropAmount,
            String thresholdTriggers,
            FetchMethod method,
            String aiSummary);
}
