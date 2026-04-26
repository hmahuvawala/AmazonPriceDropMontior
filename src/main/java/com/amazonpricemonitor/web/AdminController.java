package com.amazonpricemonitor.web;

import com.amazonpricemonitor.config.AdminProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.service.NotificationRecipientsService;
import com.amazonpricemonitor.service.PriceMonitoringService;
import com.amazonpricemonitor.service.SchedulerSettingsService;
import com.amazonpricemonitor.service.notify.Notifier;
import com.amazonpricemonitor.web.dto.NotificationRecipientsResponse;
import com.amazonpricemonitor.web.dto.SchedulerSettingsResponse;
import com.amazonpricemonitor.web.dto.UpdateNotificationRecipientsRequest;
import com.amazonpricemonitor.web.dto.UpdateSchedulerSettingsRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PriceMonitoringService priceMonitoringService;
    private final SchedulerSettingsService schedulerSettingsService;
    private final NotificationRecipientsService notificationRecipientsService;
    private final Notifier notifier;
    private final AdminProperties adminProperties;

    public AdminController(
            PriceMonitoringService priceMonitoringService,
            SchedulerSettingsService schedulerSettingsService,
            NotificationRecipientsService notificationRecipientsService,
            Notifier notifier,
            AdminProperties adminProperties) {
        this.priceMonitoringService = priceMonitoringService;
        this.schedulerSettingsService = schedulerSettingsService;
        this.notificationRecipientsService = notificationRecipientsService;
        this.notifier = notifier;
        this.adminProperties = adminProperties;
    }

    @PostMapping("/run-checks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runChecksNow() {
        priceMonitoringService.runChecksForActiveProducts();
    }

    /**
     * Sends one synthetic “price drop” through the real {@link Notifier} (log / email / SMS).
     * Does not read or write {@code price_check} rows. Disabled unless
     * {@code app.admin.allow-test-notification=true}.
     */
    @PostMapping("/send-test-notification")
    public ResponseEntity<Map<String, String>> sendTestNotification() {
        if (!adminProperties.isAllowTestNotification()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        MonitoredProduct probe = new MonitoredProduct();
        probe.setAmazonUrl("https://www.amazon.com/dp/TEST-NOTIFICATION");
        probe.setDisplayName("Connectivity test (not a real product or price change)");
        String summary =
                "Manual test via POST /api/admin/send-test-notification — no database prices were changed.";
        notifier.notifyPriceDrop(
                probe,
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                new BigDecimal("20.00"),
                new BigDecimal("20.00"),
                "TEST_ENDPOINT",
                FetchMethod.JSOUP,
                summary);
        return ResponseEntity.accepted()
                .body(Map.of(
                        "status",
                        "dispatched",
                        "hint",
                        "Check container logs for notification.sent or notification.failed, and your email/SMS inbox."));
    }

    @GetMapping("/scheduler-settings")
    public SchedulerSettingsResponse getSchedulerSettings() {
        return new SchedulerSettingsResponse(schedulerSettingsService.getCheckIntervalMs());
    }

    @PutMapping("/scheduler-settings")
    public SchedulerSettingsResponse putSchedulerSettings(@Valid @RequestBody UpdateSchedulerSettingsRequest body) {
        schedulerSettingsService.updateCheckIntervalMs(body.checkIntervalMs());
        return new SchedulerSettingsResponse(schedulerSettingsService.getCheckIntervalMs());
    }

    @GetMapping("/notification-recipients")
    public NotificationRecipientsResponse getNotificationRecipients() {
        return new NotificationRecipientsResponse(
                notificationRecipientsService.getEmailToCsv(),
                notificationRecipientsService.getSmsToCsv());
    }

    @PutMapping("/notification-recipients")
    public NotificationRecipientsResponse putNotificationRecipients(
            @RequestBody UpdateNotificationRecipientsRequest body) {
        notificationRecipientsService.updateEmailRecipients(body.emailToCsv());
        notificationRecipientsService.updateSmsRecipients(body.smsToCsv());
        return new NotificationRecipientsResponse(
                notificationRecipientsService.getEmailToCsv(),
                notificationRecipientsService.getSmsToCsv());
    }
}
