package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.config.SmsProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.service.NotificationRecipientsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class SmsNotifier implements ChannelNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    /** Max total SMS body length to keep cost bounded (~2 GSM segments). */
    static final int MAX_SMS_BODY_LENGTH = 320;

    private final SmsProperties smsProperties;
    private final NotificationRecipientsService recipientsService;
    private final RestClient twilioRestClient;

    public SmsNotifier(
            SmsProperties smsProperties,
            NotificationRecipientsService recipientsService,
            RestClient twilioRestClient) {
        this.smsProperties = smsProperties;
        this.recipientsService = recipientsService;
        this.twilioRestClient = twilioRestClient;
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
        List<String> toNumbers = recipientsService.getSmsRecipients();
        if (!smsProperties.hasTwilioCredentials() || toNumbers.isEmpty()) {
            MDC.put("event", "notification.failed");
            MDC.put("channel", "sms");
            MDC.put("reason", "not_configured");
            try {
                log.warn(
                        "SMS notifier enabled but Twilio credentials/from/to not configured; skipping for productId={}",
                        product.getId());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
                MDC.remove("reason");
            }
            return;
        }

        String label = resolveLabel(product);
        String body = buildSmsBody(
                product,
                label,
                previousPrice,
                newPrice,
                dropPercent,
                dropAmount,
                thresholdTriggers,
                method,
                aiSummary);

        String uri = buildMessagesUri();
        String authHeader = basicAuthHeader(smsProperties.getAccountSid(), smsProperties.getAuthToken());

        for (String toNumber : toNumbers) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("From", smsProperties.getFrom());
            form.add("To", toNumber);
            form.add("Body", body);

            try {
                twilioRestClient
                        .post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .toBodilessEntity();
                MDC.put("event", "notification.sent");
                MDC.put("channel", "sms");
                try {
                    log.info("Twilio SMS accepted for productId={} to={}", product.getId(), maskPhone(toNumber));
                } finally {
                    MDC.remove("event");
                    MDC.remove("channel");
                }
            } catch (RestClientException ex) {
                MDC.put("event", "notification.failed");
                MDC.put("channel", "sms");
                MDC.put("reason", "http_error");
                try {
                    log.warn(
                            "Twilio SMS delivery failed for productId={} to={}: {}",
                            product.getId(),
                            maskPhone(toNumber),
                            ex.toString());
                } finally {
                    MDC.remove("event");
                    MDC.remove("channel");
                    MDC.remove("reason");
                }
            }
        }
    }

    private String buildMessagesUri() {
        String base = smsProperties.getBaseUrl().replaceAll("/$", "");
        return base + "/Accounts/" + smsProperties.getAccountSid() + "/Messages.json";
    }

    private static String basicAuthHeader(String accountSid, String authToken) {
        String raw = accountSid + ":" + authToken;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private static String maskPhone(String e164) {
        if (e164 == null || e164.length() < 6) {
            return "***";
        }
        return e164.substring(0, 4) + "…" + e164.substring(e164.length() - 2);
    }

    private static String resolveLabel(MonitoredProduct product) {
        if (product.getDisplayName() != null && !product.getDisplayName().isBlank()) {
            return product.getDisplayName();
        }
        return product.getAmazonUrl();
    }

    /**
     * Facts first; optional AI summary appended and truncated so total length stays under
     * {@link #MAX_SMS_BODY_LENGTH}.
     */
    static String buildSmsBody(
            MonitoredProduct product,
            String label,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal dropPercent,
            BigDecimal dropAmount,
            String thresholdTriggers,
            FetchMethod method,
            String aiSummary) {
        String facts = String.format(
                "Price drop: %s | %s->%s USD | %.2f%% (%s) | %s | %s | %s",
                truncate(label, 80),
                previousPrice.setScale(2, RoundingMode.HALF_UP),
                newPrice.setScale(2, RoundingMode.HALF_UP),
                dropPercent,
                dropAmount.setScale(2, RoundingMode.HALF_UP),
                thresholdTriggers,
                method.name(),
                truncate(product.getAmazonUrl(), 120));

        if (aiSummary == null || aiSummary.isBlank()) {
            return truncate(facts, MAX_SMS_BODY_LENGTH);
        }

        String summaryHeader = " | 7d: ";
        int budgetForSummary = MAX_SMS_BODY_LENGTH - facts.length() - summaryHeader.length();
        if (budgetForSummary <= 8) {
            return truncate(facts, MAX_SMS_BODY_LENGTH);
        }
        String summaryPart = truncate(aiSummary.trim(), budgetForSummary);
        String combined = facts + summaryHeader + summaryPart;
        return truncate(combined, MAX_SMS_BODY_LENGTH);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        if (maxLen <= 3) {
            return s.substring(0, maxLen);
        }
        return s.substring(0, maxLen - 3) + "...";
    }
}
