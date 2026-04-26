package com.amazonpricemonitor.service.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonpricemonitor.config.EmailProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.service.NotificationRecipientsService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailNotifierTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private NotificationRecipientsService recipientsService;

    private EmailProperties emailProperties;

    @BeforeEach
    void setUp() {
        emailProperties = new EmailProperties();
        emailProperties.setEnabled(true);
        emailProperties.setFrom("alerts@example.com");
        emailProperties.setSubjectPrefix("CustomPrefix");
    }

    @Test
    void sendsPlainTextWithPriceFactsAndListingUrl() {
        when(recipientsService.getEmailRecipients())
                .thenReturn(List.of("user1@example.com", "user2@example.com"));
        EmailNotifier notifier = new EmailNotifier(emailProperties, recipientsService, javaMailSender);
        MonitoredProduct product = product("https://amazon.example/dp/B00", "Widget");

        notifier.notifyPriceDrop(
                product,
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                new BigDecimal("20.00"),
                new BigDecimal("20.00"),
                "pct>=10%",
                FetchMethod.JSOUP,
                null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("alerts@example.com");
        assertThat(sent.getTo()).containsExactly("user1@example.com", "user2@example.com");
        assertThat(sent.getSubject()).isEqualTo("CustomPrefix Price drop on Widget");
        assertThat(sent.getText())
                .contains("Price drop on: Widget")
                .contains("Previous: 100.00 USD -> Now: 80.00 USD")
                .contains("Drop: 20.00% (20.00 USD)")
                .contains("Threshold tripped: pct>=10%")
                .contains("Source: JSOUP")
                .contains("Listing: https://amazon.example/dp/B00")
                .doesNotContain("7-day summary");
    }

    @Test
    void appendsAiSummaryWhenPresent() {
        when(recipientsService.getEmailRecipients())
                .thenReturn(List.of("user1@example.com", "user2@example.com"));
        EmailNotifier notifier = new EmailNotifier(emailProperties, recipientsService, javaMailSender);
        MonitoredProduct product = product("https://amazon.example/dp/B01", null);

        notifier.notifyPriceDrop(
                product,
                new BigDecimal("50.00"),
                new BigDecimal("40.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                "abs",
                FetchMethod.ALTERLAB,
                "Prices drifted down over the week.");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        assertThat(captor.getValue().getText())
                .contains("7-day summary:")
                .contains("Prices drifted down over the week.");
    }

    @Test
    void skipsSendWhenFromMissing() {
        emailProperties.setFrom("");
        EmailNotifier notifier = new EmailNotifier(emailProperties, recipientsService, javaMailSender);
        MonitoredProduct product = product("https://a.example/x", "X");

        notifier.notifyPriceDrop(
                product,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "x",
                FetchMethod.JSOUP,
                null);

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void skipsSendWhenRecipientsEmpty() {
        when(recipientsService.getEmailRecipients()).thenReturn(List.of());
        EmailNotifier notifier = new EmailNotifier(emailProperties, recipientsService, javaMailSender);
        MonitoredProduct product = product("https://a.example/x", "X");

        notifier.notifyPriceDrop(
                product,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "x",
                FetchMethod.JSOUP,
                null);

        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
    }

    private static MonitoredProduct product(String url, String displayName) {
        MonitoredProduct p = new MonitoredProduct();
        p.setAmazonUrl(url);
        p.setDisplayName(displayName);
        return p;
    }
}
