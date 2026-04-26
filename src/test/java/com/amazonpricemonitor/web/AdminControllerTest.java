package com.amazonpricemonitor.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amazonpricemonitor.config.AdminProperties;
import com.amazonpricemonitor.domain.FetchMethod;
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
}
