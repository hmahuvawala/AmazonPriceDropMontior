package com.amazonpricemonitor.service.ai;

import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Builds a short, accurate narrative of a product's price activity over the trailing
 * {@value #WINDOW_DAYS} days, used to enrich threshold-breach notifications.
 * <p>
 * Accuracy strategy:
 * <ol>
 *   <li>All numeric facts ({@link PriceTrendStats}) are computed in pure Java from the
 *       database — never asked of the LLM.</li>
 *   <li>The LLM is instructed (system prompt) to narrate ONLY the supplied numbers, and
 *       its temperature/topP are kept low so the output stays grounded.</li>
 *   <li>If Gemini is disabled, unreachable, slow, blank, or returns garbage, a
 *       deterministic template summary derived from the same stats is returned. The
 *       alert path therefore always has a usable summary string available.</li>
 * </ol>
 */
@Service
public class PriceChangeSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PriceChangeSummaryService.class);

    static final int WINDOW_DAYS = 7;

    private static final int MAX_SUMMARY_CHARS = 280;

    private static final MathContext PCT_MATH = new MathContext(8, RoundingMode.HALF_UP);

    private static final String SYSTEM_PROMPT = """
            You are a financial-style summary writer for an internal price-monitoring tool.
            You will receive a JSON object with pre-computed numeric stats for a single product
            over the last 7 days.
            Rules (strict):
            - Use ONLY the numbers in the provided JSON. Do NOT compute, infer, or invent values.
            - If a number is not in the JSON, do not mention it.
            - Reply in 1 to 2 short sentences, no more than 60 words total.
            - No emojis, no markdown, no bullet points, no headings, no URLs.
            - Refer to the product by its displayName when natural.
            """;

    private final PriceCheckRepository priceCheckRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public PriceChangeSummaryService(
            PriceCheckRepository priceCheckRepository,
            GeminiClient geminiClient,
            ObjectMapper objectMapper) {
        this.priceCheckRepository = priceCheckRepository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Always returns a non-null, non-blank summary string suitable for a notification.
     * Falls back to a deterministic template when Gemini is unavailable or returns
     * unusable text. Never throws.
     */
    public String summarizeLast7Days(MonitoredProduct product) {
        try {
            Instant since = Instant.now().minus(Duration.ofDays(WINDOW_DAYS));
            List<PriceCheck> checks = priceCheckRepository
                    .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(product.getId(), since);
            Optional<PriceTrendStats> maybeStats = computeStats(checks);

            if (maybeStats.isEmpty() || !maybeStats.get().hasEnoughDataToNarrate()) {
                return notEnoughDataFallback(product, maybeStats.orElse(null));
            }
            PriceTrendStats stats = maybeStats.get();
            String fallback = renderFallback(product, stats);

            Optional<String> aiText = callGemini(product, stats);
            return aiText.map(PriceChangeSummaryService::sanitize)
                    .filter(s -> !s.isBlank())
                    .orElse(fallback);
        } catch (RuntimeException ex) {
            // Defensive: never let a summary failure break the alert path.
            MDC.put("event", "ai.summary.skipped");
            MDC.put("reason", "unexpected");
            try {
                log.warn("Summary generation failed; using minimal fallback: {}", ex.toString());
            } finally {
                MDC.remove("event");
                MDC.remove("reason");
            }
            return "7-day summary unavailable.";
        }
    }

    private Optional<String> callGemini(MonitoredProduct product, PriceTrendStats stats) {
        try {
            String userJson = buildUserJson(product, stats);
            return geminiClient.generate(SYSTEM_PROMPT, userJson);
        } catch (JsonProcessingException ex) {
            MDC.put("event", "ai.summary.skipped");
            MDC.put("reason", "prompt_serialization_failed");
            try {
                log.warn("Could not serialize stats payload: {}", ex.toString());
            } finally {
                MDC.remove("event");
                MDC.remove("reason");
            }
            return Optional.empty();
        }
    }

    /**
     * Package-private for direct unit testing of stats math without a Spring context.
     */
    static Optional<PriceTrendStats> computeStats(List<PriceCheck> checksAsc) {
        if (checksAsc == null || checksAsc.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal min = null;
        BigDecimal max = null;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal oldest = null;
        BigDecimal latest = null;
        String currency = null;
        int success = 0;
        int failures = 0;

        for (PriceCheck check : checksAsc) {
            if (!check.isSuccess() || check.getPriceAmount() == null) {
                failures++;
                continue;
            }
            BigDecimal price = check.getPriceAmount();
            if (oldest == null) {
                oldest = price;
            }
            latest = price;
            currency = check.getCurrency();
            sum = sum.add(price);
            if (min == null || price.compareTo(min) < 0) {
                min = price;
            }
            if (max == null || price.compareTo(max) > 0) {
                max = price;
            }
            success++;
        }

        if (success == 0) {
            return Optional.of(new PriceTrendStats(
                    null, null, null, null, null, null, null,
                    checksAsc.size(), 0, failures, null, WINDOW_DAYS));
        }

        BigDecimal avg = sum.divide(BigDecimal.valueOf(success), 2, RoundingMode.HALF_UP);
        BigDecimal absChange = latest.subtract(oldest).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pctChange = oldest.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(2)
                : latest.subtract(oldest)
                        .divide(oldest, PCT_MATH)
                        .multiply(BigDecimal.valueOf(100), PCT_MATH)
                        .setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new PriceTrendStats(
                oldest.setScale(2, RoundingMode.HALF_UP),
                latest.setScale(2, RoundingMode.HALF_UP),
                min.setScale(2, RoundingMode.HALF_UP),
                max.setScale(2, RoundingMode.HALF_UP),
                avg,
                pctChange,
                absChange,
                checksAsc.size(),
                success,
                failures,
                currency,
                WINDOW_DAYS));
    }

    private String buildUserJson(MonitoredProduct product, PriceTrendStats stats)
            throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", product.getDisplayName());
        payload.put("currency", stats.currency());
        payload.put("windowDays", stats.windowDays());
        payload.put("oldestPrice", stats.oldestPrice());
        payload.put("latestPrice", stats.latestPrice());
        payload.put("minPrice", stats.minPrice());
        payload.put("maxPrice", stats.maxPrice());
        payload.put("avgPrice", stats.avgPrice());
        payload.put("absChange", stats.absChange());
        payload.put("pctChange", stats.pctChange());
        payload.put("numChecks", stats.numChecks());
        payload.put("numSuccess", stats.numSuccess());
        payload.put("numFailures", stats.numFailures());
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Package-private for tests. Produces a deterministic, audit-friendly sentence using
     * only the supplied stats — no LLM involved. This is the safety net.
     */
    static String renderFallback(MonitoredProduct product, PriceTrendStats stats) {
        String label = product.getDisplayName() != null && !product.getDisplayName().isBlank()
                ? product.getDisplayName()
                : "this product";
        String currency = stats.currency() != null ? stats.currency() : "";
        int sign = stats.absChange().signum();
        String trendPhrase;
        if (sign == 0) {
            trendPhrase = "flat";
        } else {
            String direction = sign < 0 ? "down" : "up";
            trendPhrase = String.format(
                    "%s by %s%s",
                    direction, currency, stats.absChange().abs().toPlainString());
        }
        String summary = String.format(
                "Over the last %d days, %s is %s (%s%%): from %s%s to %s%s, "
                        + "ranging %s%s–%s%s across %d successful checks.",
                stats.windowDays(),
                label,
                trendPhrase,
                stats.pctChange().toPlainString(),
                currency,
                stats.oldestPrice().toPlainString(),
                currency,
                stats.latestPrice().toPlainString(),
                currency,
                stats.minPrice().toPlainString(),
                currency,
                stats.maxPrice().toPlainString(),
                stats.numSuccess());
        if (stats.numFailures() > 0) {
            summary = summary + " " + stats.numFailures() + " check(s) failed in this window.";
        }
        return summary;
    }

    private static String notEnoughDataFallback(MonitoredProduct product, PriceTrendStats stats) {
        String label = product.getDisplayName() != null && !product.getDisplayName().isBlank()
                ? product.getDisplayName()
                : "this product";
        if (stats == null || stats.numChecks() == 0) {
            return String.format(
                    "Not enough history to summarize the last %d days for %s.",
                    WINDOW_DAYS, label);
        }
        return String.format(
                "Only %d successful check(s) in the last %d days for %s; trend not yet meaningful.",
                stats.numSuccess(), WINDOW_DAYS, label);
    }

    /**
     * Cap length and strip control characters so a misbehaving model can never blow up
     * a Slack payload or a log line.
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (stripped.length() > MAX_SUMMARY_CHARS) {
            return stripped.substring(0, MAX_SUMMARY_CHARS - 1) + "…";
        }
        return stripped;
    }
}
