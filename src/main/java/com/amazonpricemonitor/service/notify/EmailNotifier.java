package com.amazonpricemonitor.service.notify;

import com.amazonpricemonitor.config.EmailProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.service.NotificationRecipientsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class EmailNotifier implements ChannelNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final EmailProperties emailProperties;
    private final NotificationRecipientsService recipientsService;
    private final JavaMailSender javaMailSender;

    public EmailNotifier(
            EmailProperties emailProperties,
            NotificationRecipientsService recipientsService,
            JavaMailSender javaMailSender) {
        this.emailProperties = emailProperties;
        this.recipientsService = recipientsService;
        this.javaMailSender = javaMailSender;
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
        List<String> toList = recipientsService.getEmailRecipients();
        if (!emailProperties.hasFrom() || toList.isEmpty()) {
            MDC.put("event", "notification.failed");
            MDC.put("channel", "email");
            MDC.put("reason", "not_configured");
            try {
                log.warn(
                        "Email notifier enabled but from/to not configured; skipping notification for productId={}",
                        product.getId());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
                MDC.remove("reason");
            }
            return;
        }

        String label = resolveLabel(product);
        String subject =
                String.format("%s Price drop on %s", emailProperties.getSubjectPrefix(), label);

        String body = buildBody(
                product,
                label,
                previousPrice,
                newPrice,
                dropPercent,
                dropAmount,
                thresholdTriggers,
                method,
                aiSummary);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(toList.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(body);

        try {
            javaMailSender.send(message);
            MDC.put("event", "notification.sent");
            MDC.put("channel", "email");
            try {
                log.info("Email notification sent for productId={}", product.getId());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
            }
        } catch (MailException ex) {
            MDC.put("event", "notification.failed");
            MDC.put("channel", "email");
            MDC.put("reason", "mail_error");
            try {
                log.warn("Email delivery failed for productId={}: {}", product.getId(), ex.toString());
            } finally {
                MDC.remove("event");
                MDC.remove("channel");
                MDC.remove("reason");
            }
        }
    }

    private static String resolveLabel(MonitoredProduct product) {
        if (product.getDisplayName() != null && !product.getDisplayName().isBlank()) {
            return product.getDisplayName();
        }
        return product.getAmazonUrl();
    }

    private static String buildBody(
            MonitoredProduct product,
            String label,
            BigDecimal previousPrice,
            BigDecimal newPrice,
            BigDecimal dropPercent,
            BigDecimal dropAmount,
            String thresholdTriggers,
            FetchMethod method,
            String aiSummary) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(
                "Price drop on: %s%n%n"
                        + "Previous: %s USD -> Now: %s USD%n"
                        + "Drop: %.2f%% (%s USD)%n"
                        + "Threshold tripped: %s%n"
                        + "Source: %s%n%n"
                        + "Listing: %s",
                label,
                previousPrice.setScale(2, RoundingMode.HALF_UP),
                newPrice.setScale(2, RoundingMode.HALF_UP),
                dropPercent,
                dropAmount.setScale(2, RoundingMode.HALF_UP),
                thresholdTriggers,
                method.name(),
                product.getAmazonUrl()));
        if (aiSummary != null && !aiSummary.isBlank()) {
            text.append(String.format("%n%n7-day summary:%n%s", aiSummary));
        }
        return text.toString();
    }
}
