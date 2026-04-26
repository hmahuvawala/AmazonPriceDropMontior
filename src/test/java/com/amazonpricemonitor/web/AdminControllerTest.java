package com.amazonpricemonitor.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amazonpricemonitor.config.AdminProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.service.NotificationRecipientsService;
import com.amazonpricemonitor.service.PriceMonitoringService;
import com.amazonpricemonitor.service.SchedulerSettingsService;
import com.amazonpricemonitor.service.notify.Notifier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PriceMonitoringService priceMonitoringService;

    @MockBean
    private SchedulerSettingsService schedulerSettingsService;

    @MockBean
    private NotificationRecipientsService notificationRecipientsService;

    @MockBean
    private Notifier notifier;

    @MockBean
    private AdminProperties adminProperties;

    @Test
    void sendTestNotificationReturns404WhenDisabled() throws Exception {
        when(adminProperties.isAllowTestNotification()).thenReturn(false);

        mockMvc.perform(post("/api/admin/send-test-notification").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(notifier, never()).notifyPriceDrop(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendTestNotificationDispatchesWhenEnabled() throws Exception {
        when(adminProperties.isAllowTestNotification()).thenReturn(true);

        mockMvc.perform(post("/api/admin/send-test-notification").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("dispatched"));

        verify(notifier)
                .notifyPriceDrop(
                        any(),
                        eq(new BigDecimal("100.00")),
                        eq(new BigDecimal("80.00")),
                        eq(new BigDecimal("20.00")),
                        eq(new BigDecimal("20.00")),
                        eq("TEST_ENDPOINT"),
                        eq(FetchMethod.JSOUP),
                        anyString());
    }

    @Test
    void getNotificationRecipientsReturnsCurrentCsvs() throws Exception {
        when(notificationRecipientsService.getEmailToCsv())
                .thenReturn("alerts@example.com,ops@example.com");
        when(notificationRecipientsService.getSmsToCsv()).thenReturn("+15551231234");

        mockMvc.perform(get("/api/admin/notification-recipients").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailToCsv").value("alerts@example.com,ops@example.com"))
                .andExpect(jsonPath("$.smsToCsv").value("+15551231234"));
    }

    @Test
    void putNotificationRecipientsUpdatesAndReturnsCurrent() throws Exception {
        // Controller re-reads the persisted CSVs after the updates run, so stub the post-update reads.
        when(notificationRecipientsService.getEmailToCsv()).thenReturn("alerts@example.com");
        when(notificationRecipientsService.getSmsToCsv()).thenReturn("+15551231234");

        String body = "{\"emailToCsv\":\"alerts@example.com\",\"smsToCsv\":\"+15551231234\"}";

        mockMvc.perform(put("/api/admin/notification-recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailToCsv").value("alerts@example.com"))
                .andExpect(jsonPath("$.smsToCsv").value("+15551231234"));

        verify(notificationRecipientsService).updateEmailRecipients("alerts@example.com");
        verify(notificationRecipientsService).updateSmsRecipients("+15551231234");
    }

    @Test
    void putNotificationRecipientsReturns400OnInvalidEmail() throws Exception {
        doThrow(new IllegalArgumentException("Invalid email address: not-an-email"))
                .when(notificationRecipientsService)
                .updateEmailRecipients(anyString());

        String body = "{\"emailToCsv\":\"not-an-email\",\"smsToCsv\":\"\"}";

        mockMvc.perform(put("/api/admin/notification-recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email address: not-an-email"));

        verify(notificationRecipientsService, never()).updateSmsRecipients(anyString());
    }
}
