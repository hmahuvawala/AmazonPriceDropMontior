package com.amazonpricemonitor.service.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.amazonpricemonitor.config.NotificationProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SlackNotifierTest {

    private MockRestServiceServer mockServer;
    private RestClient.Builder builder;
    private NotificationProperties properties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        properties = new NotificationProperties();
        properties.setType("slack");
        properties.getSlack().setWebhookUrl("https://hooks.slack.services/TEST/WEBHOOK");
    }

    @AfterEach
    void tearDown() {
        mockServer.reset();
    }

    @Test
    void postsJsonWithTextAndEscapesHtmlEntitiesInLabel() {
        SlackNotifier notifier = new SlackNotifier(properties, builder);

        mockServer
                .expect(ExpectedCount.once(), requestTo(URI.create(properties.getSlack().getWebhookUrl())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(SlackNotifierTest::assertSlackJsonEscapesLabel)
                .andRespond(withSuccess());

        MonitoredProduct p = new MonitoredProduct();
        p.setDisplayName("A & B <tag>");
        p.setAmazonUrl("https://amazon.test/dp/1");

        assertThatCode(
                        () -> notifier.notifyPriceDrop(
                                p,
                                new BigDecimal("100"),
                                new BigDecimal("90"),
                                new BigDecimal("10.00"),
                                new BigDecimal("10.00"),
                                "PCT",
                                FetchMethod.JSOUP,
                                null))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void blankWebhookPerformsNoHttpRequest() {
        properties.getSlack().setWebhookUrl("   ");
        SlackNotifier notifier = new SlackNotifier(properties, builder);

        notifier.notifyPriceDrop(
                productWithUrl("https://x.test"),
                BigDecimal.TEN,
                BigDecimal.ONE,
                new BigDecimal("9"),
                new BigDecimal("9"),
                "PCT",
                FetchMethod.JSOUP,
                null);

        mockServer.verify();
    }

    @Test
    void serverErrorDoesNotThrow() {
        SlackNotifier notifier = new SlackNotifier(properties, builder);

        mockServer
                .expect(ExpectedCount.once(), requestTo(URI.create(properties.getSlack().getWebhookUrl())))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatCode(
                        () -> notifier.notifyPriceDrop(
                                productWithUrl("https://amazon.test/dp/2"),
                                new BigDecimal("100"),
                                new BigDecimal("90"),
                                new BigDecimal("10.00"),
                                new BigDecimal("10.00"),
                                "PCT",
                                FetchMethod.JSOUP,
                                null))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void aiSummaryIsAppendedAndHtmlEscapedInPayload() {
        SlackNotifier notifier = new SlackNotifier(properties, builder);

        mockServer
                .expect(ExpectedCount.once(), requestTo(URI.create(properties.getSlack().getWebhookUrl())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(SlackNotifierTest::assertSlackJsonContainsEscapedSummary)
                .andRespond(withSuccess());

        notifier.notifyPriceDrop(
                productWithUrl("https://amazon.test/dp/3"),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                "PCT",
                FetchMethod.JSOUP,
                "Trend: down 10% (<see chart> & dashboard).");

        mockServer.verify();
    }

    @Test
    void blankAiSummaryIsOmittedFromPayload() {
        SlackNotifier notifier = new SlackNotifier(properties, builder);

        mockServer
                .expect(ExpectedCount.once(), requestTo(URI.create(properties.getSlack().getWebhookUrl())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(SlackNotifierTest::assertSlackJsonOmitsSummaryHeader)
                .andRespond(withSuccess());

        notifier.notifyPriceDrop(
                productWithUrl("https://amazon.test/dp/4"),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                "PCT",
                FetchMethod.JSOUP,
                "   ");

        mockServer.verify();
    }

    private static MonitoredProduct productWithUrl(String url) {
        MonitoredProduct p = new MonitoredProduct();
        p.setDisplayName("n");
        p.setAmazonUrl(url);
        return p;
    }

    private static void assertSlackJsonEscapesLabel(org.springframework.http.client.ClientHttpRequest request)
            throws IOException {
        assertThat(request).isInstanceOf(MockClientHttpRequest.class);
        String json = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"text\"");
        assertThat(json).contains("&lt;");
        assertThat(json).contains("&gt;");
        assertThat(json).contains("&amp;");
    }

    private static void assertSlackJsonContainsEscapedSummary(
            org.springframework.http.client.ClientHttpRequest request) throws IOException {
        assertThat(request).isInstanceOf(MockClientHttpRequest.class);
        String json = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("7-day summary");
        assertThat(json).contains("&lt;see chart&gt;");
        assertThat(json).contains("&amp;");
        assertThat(json).doesNotContain("<see chart>");
    }

    private static void assertSlackJsonOmitsSummaryHeader(
            org.springframework.http.client.ClientHttpRequest request) throws IOException {
        assertThat(request).isInstanceOf(MockClientHttpRequest.class);
        String json = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("7-day summary");
    }
}
