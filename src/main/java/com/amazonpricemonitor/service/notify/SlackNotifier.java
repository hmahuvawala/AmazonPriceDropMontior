package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.config.NotificationProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.notification.type", havingValue = "slack")
public class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final NotificationProperties notificationProperties;
    private final RestClient restClient;

    public SlackNotifier(NotificationProperties notificationProperties, RestClient.Builder restClientBuilder) {
        this.notificationProperties = notificationProperties;
        this.restClient = restClientBuilder.build();
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
        if (!notificationProperties.getSlack().isConfigured()) {
            MDC.put("event", "notification.failed");
            MDC.put("channel", "slack");
            MDC.put("reason", "webhook_not_configured");
            try {
                log.warn("Slack webhook not configured; skipping notification for productId={}", product.getId());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
                MDC.remove("reason");
            }
            return;
        }
        String label = product.getDisplayName() != null && !product.getDisplayName().isBlank()
                ? product.getDisplayName()
                : product.getAmazonUrl();
        StringBuilder text = new StringBuilder();
        text.append(String.format(
                ":moneybag: Price drop on *%s*%n"
                        + "Previous: %s %s → Now: %s %s%n"
                        + "Drop: %.2f%% (%s %s)%n"
                        + "Threshold tripped: *%s*%n"
                        + "Source: %s%n"
                        + "<%s|Open listing>",
                escapeSlack(label),
                previousPrice.setScale(2, RoundingMode.HALF_UP),
                "USD",
                newPrice.setScale(2, RoundingMode.HALF_UP),
                "USD",
                dropPercent,
                dropAmount.setScale(2, RoundingMode.HALF_UP),
                "USD",
                escapeSlack(thresholdTriggers),
                method.name(),
                product.getAmazonUrl()));
        if (aiSummary != null && !aiSummary.isBlank()) {
            text.append(String.format("%n_7-day summary:_ %s", escapeSlack(aiSummary)));
        }

        Map<String, String> body = Map.of("text", text.toString());
        try {
            restClient
                    .post()
                    .uri(notificationProperties.getSlack().getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            MDC.put("event", "notification.sent");
            MDC.put("channel", "slack");
            try {
                log.info("Slack webhook accepted notification for productId={}", product.getId());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
            }
        } catch (RestClientException ex) {
            MDC.put("event", "notification.failed");
            MDC.put("channel", "slack");
            MDC.put("reason", "http_error");
            try {
                log.warn("Slack webhook delivery failed for productId={}: {}", product.getId(), ex.toString());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
                MDC.remove("reason");
            }
        }
    }

    private static String escapeSlack(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
