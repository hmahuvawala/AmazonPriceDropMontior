package com.amazonpricemonitor.service;

import com.amazonpricemonitor.config.AlterLabProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AlterLabPriceClient {

    private static final Logger log = LoggerFactory.getLogger(AlterLabPriceClient.class);

    private final AlterLabProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AlterLabPriceClient(AlterLabProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(30, properties.getRequestTimeoutSeconds())));
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<PriceQuote> fetch(String productUrl) {
        if (!properties.isConfigured()) {
            log.warn("AlterLab API key is not configured; skipping AlterLab fallback");
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "url", productUrl,
                "formats", List.of("json"),
                "sync", true);
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize AlterLab request: {}", ex.toString());
            return Optional.empty();
        }
        try {
            String responseJson = restClient
                    .post()
                    .uri(properties.getScrapePath())
                    .header("X-API-Key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (responseJson == null || responseJson.isBlank()) {
                return Optional.empty();
            }
            return extractPrice(responseJson);
        } catch (RestClientException ex) {
            log.warn("AlterLab request failed for url={}: {}", productUrl, ex.toString());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("AlterLab response handling failed for url={}: {}", productUrl, ex.toString());
            return Optional.empty();
        }
    }

    private Optional<PriceQuote> extractPrice(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode jsonBlock = root.path("content").path("json");
            if (jsonBlock.isMissingNode() || jsonBlock.isNull()) {
                return Optional.empty();
            }
            Optional<BigDecimal> price = readPriceNode(jsonBlock.get("price"));
            if (price.isEmpty()) {
                price = readPriceNode(jsonBlock.path("offers").path("price"));
            }
            if (price.isEmpty()) {
                return Optional.empty();
            }
            String currency = textOrDefault(jsonBlock.path("currency"), "USD");
            return Optional.of(new PriceQuote(price.get(), currency, FetchMethod.ALTERLAB));
        } catch (Exception ex) {
            log.debug("Failed to parse AlterLab JSON: {}", ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> readPriceNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.decimalValue());
        }
        if (node.isTextual()) {
            return MoneyParsing.fromPlainText(node.asText());
        }
        return Optional.empty();
    }

    private static String textOrDefault(JsonNode node, String defaultValue) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            return node.asText();
        }
        return defaultValue;
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
