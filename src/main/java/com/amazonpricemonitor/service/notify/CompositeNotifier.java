package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Fan-out to all {@link ChannelNotifier} beans (log, email, SMS). Per-delegate failures are
 * logged and do not suppress other channels.
 */
@Component
@Primary
public class CompositeNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(CompositeNotifier.class);

    private final List<ChannelNotifier> delegates;

    public CompositeNotifier(List<ChannelNotifier> delegates) {
        this.delegates = delegates != null ? List.copyOf(delegates) : List.of();
    }

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
        if (delegates.isEmpty()) {
            return;
        }
        for (ChannelNotifier delegate : delegates) {
            try {
                delegate.notifyPriceDrop(
                        product,
                        previousPrice,
                        newPrice,
                        dropPercent,
                        dropAmount,
                        thresholdTriggers,
                        method,
                        aiSummary);
            } catch (RuntimeException ex) {
                log.warn(
                        "Notifier {} failed for productId={}",
                        delegate.getClass().getSimpleName(),
                        product.getId(),
                        ex);
            }
        }
    }
}
