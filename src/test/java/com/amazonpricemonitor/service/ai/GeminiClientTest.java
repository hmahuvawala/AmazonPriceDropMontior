package com.amazonpricemonitor.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

    private static final String VALID_RESPONSE = """
            {
              "candidates": [
                {"content": {"parts": [{"text": "Down 10% over the last 7 days."}]}}
              ]
            }
            """;

    private static final String EMPTY_CANDIDATES_RESPONSE = """
            {"candidates": []}
            """;

    private static final String SAFETY_BLOCKED_RESPONSE = """
            {
              "candidates": [
                {"finishReason": "SAFETY"}
              ]
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private RestClient restClient;
    private GeminiProperties properties;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("gemini-2.0-flash");
        properties.setBaseUrl("https://gemini.test/v1beta");
        properties.setTimeoutMs(2000);
    }

    @Test
    void disabledClientNeverCallsTheApi() {
        properties.setEnabled(false);
        GeminiClient client = new GeminiClient(properties, restClient);

        Optional<String> result = client.generate("sys", "{\"a\":1}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void blankApiKeyShortCircuitsWithoutHttpCall() {
        properties.setApiKey("");
        GeminiClient client = new GeminiClient(properties, restClient);

        Optional<String> result = client.generate("sys", "{\"a\":1}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void happyPathReturnsTextFromFirstCandidate() {
        GeminiClient client = new GeminiClient(properties, restClient);
        String expectedUri = "https://gemini.test/v1beta/models/gemini-2.0-flash:generateContent?key=test-key";

        mockServer
                .expect(ExpectedCount.once(), requestTo(URI.create(expectedUri)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(VALID_RESPONSE, MediaType.APPLICATION_JSON));

        Optional<String> result = client.generate("sys", "{\"price\":100}");

        assertThat(result).contains("Down 10% over the last 7 days.");
        mockServer.verify();
    }

    @Test
    void emptyCandidatesYieldsEmpty() {
        GeminiClient client = new GeminiClient(properties, restClient);

        mockServer
                .expect(ExpectedCount.once(), requestTo(uri(properties)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(EMPTY_CANDIDATES_RESPONSE, MediaType.APPLICATION_JSON));

        Optional<String> result = client.generate("sys", "{}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void safetyBlockedResponseYieldsEmpty() {
        GeminiClient client = new GeminiClient(properties, restClient);

        mockServer
                .expect(ExpectedCount.once(), requestTo(uri(properties)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(SAFETY_BLOCKED_RESPONSE, MediaType.APPLICATION_JSON));

        Optional<String> result = client.generate("sys", "{}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void clientErrorYieldsEmpty() {
        GeminiClient client = new GeminiClient(properties, restClient);

        mockServer
                .expect(ExpectedCount.once(), requestTo(uri(properties)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"bad\"}".getBytes(StandardCharsets.UTF_8))
                        .contentType(MediaType.APPLICATION_JSON));

        Optional<String> result = client.generate("sys", "{}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void serverErrorYieldsEmpty() {
        GeminiClient client = new GeminiClient(properties, restClient);

        mockServer
                .expect(ExpectedCount.once(), requestTo(uri(properties)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        Optional<String> result = client.generate("sys", "{}");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    private static URI uri(GeminiProperties props) {
        return URI.create(String.format(
                "%s/models/%s:generateContent?key=%s",
                props.getBaseUrl(), props.getModel(), props.getApiKey()));
    }
}
