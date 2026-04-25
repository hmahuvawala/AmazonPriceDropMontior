package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.type", havingValue = "log", matchIfMissing = true)
public class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    public void notifyPriceDrop(
            MonitoredProduct product,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal dropPercent,
            BigDecimal dropAmount,
            String thresholdTriggers,
            FetchMethod method,
            String aiSummary) {
        MDC.put("event", "notification.sent");
        MDC.put("channel", "log");
        MDC.put("previousPrice", previousPrice.toPlainString());
        MDC.put("newPrice", newPrice.toPlainString());
        MDC.put("dropPct", dropPercent.toPlainString());
        MDC.put("dropAmount", dropAmount.toPlainString());
        MDC.put("thresholdTriggers", thresholdTriggers);
        MDC.put("fetchMethod", method.name());
        if (aiSummary != null && !aiSummary.isBlank()) {
            MDC.put("aiSummary", aiSummary);
        }
        try {
            log.info("price drop notification (log channel)");
        } finally {
            MDC.remove("event");
            MDC.remove("channel");
            MDC.remove("previousPrice");
            MDC.remove("newPrice");
            MDC.remove("dropPct");
            MDC.remove("dropAmount");
            MDC.remove("thresholdTriggers");
            MDC.remove("fetchMethod");
            MDC.remove("aiSummary");
        }
    }
}
