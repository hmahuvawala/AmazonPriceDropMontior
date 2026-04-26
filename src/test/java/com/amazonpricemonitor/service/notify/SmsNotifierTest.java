package com.amazonpricemonitor.service.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.amazonpricemonitor.config.SmsProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SmsNotifierTest {

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private RestClient restClient;
    private SmsProperties smsProperties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        smsProperties = new SmsProperties();
        smsProperties.setEnabled(true);
        smsProperties.setAccountSid("ACtestsid");
        smsProperties.setAuthToken("testtoken");
        smsProperties.setFrom("+15550001111");
        smsProperties.setTo("+15550002222");
        smsProperties.setBaseUrl("https://twilio.test");
        smsProperties.setTimeoutMs(3000);
    }

    @Test
    void postsFormEncodedMessageWithBasicAuth() {
        String expectedUri = "https://twilio.test/Accounts/ACtestsid/Messages.json";
        String expectedAuth =
                "Basic " + Base64.getEncoder().encodeToString("ACtestsid:testtoken".getBytes(StandardCharsets.UTF_8));

        mockServer
                .expect(ExpectedCount.once(), requestTo(expectedUri))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(content().string(allOf(
                        containsString("From="),
                        containsString("15550001111"),
                        containsString("To="),
                        containsString("15550002222"),
                        containsString("Body="))))
                .andRespond(withSuccess());

        SmsNotifier notifier = new SmsNotifier(smsProperties, restClient);
        MonitoredProduct product = product("https://amazon.example/dp/B00", "Gadget");

        notifier.notifyPriceDrop(
                product,
                new BigDecimal("100.00"),
                new BigDecimal("90.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                "pct",
                FetchMethod.JSOUP,
                null);

        mockServer.verify();
    }

    @Test
    void skipsHttpWhenTwilioNotConfigured() {
        smsProperties.setAccountSid("");
        SmsNotifier notifier = new SmsNotifier(smsProperties, restClient);
        MonitoredProduct product = product("https://a.example/x", "X");

        notifier.notifyPriceDrop(
                product,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "t",
                FetchMethod.JSOUP,
                null);

        mockServer.verify();
    }

    @Test
    void httpErrorStillCompletesWithoutThrowing() {
        mockServer
                .expect(ExpectedCount.once(), requestTo("https://twilio.test/Accounts/ACtestsid/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        SmsNotifier notifier = new SmsNotifier(smsProperties, restClient);
        MonitoredProduct product = product("https://a.example/p", "P");

        notifier.notifyPriceDrop(
                product,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "x",
                FetchMethod.JSOUP,
                null);

        mockServer.verify();
    }

    @Test
    void buildSmsBody_respectsMaxLengthAndTruncatesSummary() {
        MonitoredProduct product = product("https://amazon.example/dp/VERYLONGURLPATH", "Name");
        String longSummary = "x".repeat(400);
        String body = SmsNotifier.buildSmsBody(
                product,
                "ShortLabel",
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                "pct>=5",
                FetchMethod.JSOUP,
                longSummary);

        assertThat(body.length()).isLessThanOrEqualTo(SmsNotifier.MAX_SMS_BODY_LENGTH);
        assertThat(body).contains("7d:");
    }

    @Test
    void buildSmsBody_omitsSummaryWhenBlank() {
        MonitoredProduct product = product("https://a.example/x", null);
        String body = SmsNotifier.buildSmsBody(
                product,
                "L",
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                "t",
                FetchMethod.ALTERLAB,
                "  ");

        assertThat(body).doesNotContain("7d:");
    }

    private static MonitoredProduct product(String url, String displayName) {
        MonitoredProduct p = new MonitoredProduct();
        p.setAmazonUrl(url);
        p.setDisplayName(displayName);
        return p;
    }
}
