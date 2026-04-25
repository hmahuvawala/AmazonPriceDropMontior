package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notification.type", havingValue = "noop")
public class NoopNotifier implements Notifier {

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
        // intentionally empty — tests or "alerts off"
    }
}
