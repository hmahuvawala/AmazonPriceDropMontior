package com.amazonpricemonitor.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin REST adapter for Google Gemini's {@code generateContent} endpoint.
 * <p>
 * Design constraints (these are non-negotiable for the alert path):
 * <ul>
 *   <li><b>Bounded.</b> A hard connect+read timeout from {@link GeminiProperties#getTimeoutMs()}
 *       is enforced via the request factory; the alert path never blocks longer than that.</li>
 *   <li><b>Side-effect-free on failure.</b> Any error — network, 4xx/5xx, parsing, empty
 *       candidates, safety block — yields {@link Optional#empty()} so the caller can fall back
 *       deterministically. We never throw to callers.</li>
 *   <li><b>No retries.</b> Retries would compound the worst-case latency seen by the alert.</li>
 * </ul>
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties properties;
    private final RestClient restClient;

    /**
     * The {@code RestClient} is constructed in {@code AppConfiguration} so that the
     * timeout-configuring request factory is applied exactly once. Tests can supply a
     * {@code RestClient} bound to a {@code MockRestServiceServer} via the same constructor.
     */
    public GeminiClient(
            GeminiProperties properties,
            @Qualifier("geminiRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * Calls Gemini and returns the model's text on success, or {@link Optional#empty()}
     * on any failure (logged with structured MDC fields and {@code event=gemini.call.*}).
     *
     * @param systemInstruction strict system prompt that constrains the model to facts only
     * @param userJsonContent   the user-message content (we pass a JSON-stringified stats blob)
     */
    public Optional<String> generate(String systemInstruction, String userJsonContent) {
        if (!properties.isCallable()) {
            return Optional.empty();
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userJsonContent)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "topP", 0.8,
                        "maxOutputTokens", properties.getMaxOutputTokens()));

        String uri = String.format(
                "%s/models/%s:generateContent?key=%s",
                properties.getBaseUrl(),
                properties.getModel(),
                properties.getApiKey());

        long startNanos = System.nanoTime();
        try {
            GeminiResponse response = restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            Optional<String> text = extractText(response);
            if (text.isEmpty()) {
                logFailure("empty_candidates", latencyMs, null);
                return Optional.empty();
            }
            logSuccess(latencyMs);
            return text;
        } catch (RestClientException ex) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logFailure("http_error", latencyMs, ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logFailure("unexpected", latencyMs, ex);
            return Optional.empty();
        }
    }

    private static Optional<String> extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return Optional.empty();
        }
        for (Candidate candidate : response.candidates()) {
            if (candidate == null || candidate.content() == null
                    || candidate.content().parts() == null) {
                continue;
            }
            for (Part part : candidate.content().parts()) {
                if (part != null && part.text() != null && !part.text().isBlank()) {
                    return Optional.of(part.text());
                }
            }
        }
        return Optional.empty();
    }

    private static void logSuccess(long latencyMs) {
        MDC.put("event", "gemini.call.success");
        MDC.put("latencyMs", String.valueOf(latencyMs));
        try {
            log.info("Gemini summary generated");
        } finally {
            MDC.remove("event");
            MDC.remove("latencyMs");
        }
    }

    private static void logFailure(String reason, long latencyMs, Throwable ex) {
        MDC.put("event", "gemini.call.failure");
        MDC.put("reason", reason);
        MDC.put("latencyMs", String.valueOf(latencyMs));
        try {
            if (ex != null) {
                log.warn("Gemini summary call failed: {}", ex.toString());
            } else {
                log.warn("Gemini summary call returned no usable text");
            }
        } finally {
            MDC.remove("event");
            MDC.remove("reason");
            MDC.remove("latencyMs");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}
}
