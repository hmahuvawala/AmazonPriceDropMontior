package com.amazonpricemonitor.service;

import com.amazonpricemonitor.config.SlackProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SlackNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);

    private final SlackProperties properties;
    private final RestClient restClient;

    public SlackNotificationService(SlackProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public void notifyPriceDrop(
            MonitoredProduct product,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal dropPercent,
            FetchMethod method) {
        if (!properties.isConfigured()) {
            log.debug("Slack webhook not configured; skipping notification for productId={}", product.getId());
            return;
        }
        String label = product.getDisplayName() != null && !product.getDisplayName().isBlank()
                ? product.getDisplayName()
                : product.getAmazonUrl();
        String text = String.format(
                ":moneybag: Price drop on *%s*%nPrevious: %s %s → Now: %s %s (%.2f%% drop, source: %s)%n<%s|Open listing>",
                escapeSlack(label),
                previousPrice.setScale(2, RoundingMode.HALF_UP),
                "USD",
                newPrice.setScale(2, RoundingMode.HALF_UP),
                "USD",
                dropPercent,
                method.name(),
                product.getAmazonUrl());

        Map<String, String> body = Map.of("text", text);
        try {
            restClient
                    .post()
                    .uri(properties.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Slack webhook delivery failed for productId={}: {}", product.getId(), ex.toString());
        }
    }

    private static String escapeSlack(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
