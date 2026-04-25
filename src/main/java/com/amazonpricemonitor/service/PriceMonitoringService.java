package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.MonitoredProductRepository;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.amazonpricemonitor.service.ai.PriceChangeSummaryService;
import com.amazonpricemonitor.service.notify.Notifier;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(PriceMonitoringService.class);

    private static final MathContext DROP_MATH = new MathContext(8, RoundingMode.HALF_UP);

    private static final int MAX_AMAZON_URL_MDC = 512;

    private final MonitoredProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;
    private final CompositePriceFetcher compositePriceFetcher;
    private final Notifier notifier;
    private final PriceChangeSummaryService priceChangeSummaryService;

    public PriceMonitoringService(
            MonitoredProductRepository productRepository,
            PriceCheckRepository priceCheckRepository,
            CompositePriceFetcher compositePriceFetcher,
            Notifier notifier,
            PriceChangeSummaryService priceChangeSummaryService) {
        this.productRepository = productRepository;
        this.priceCheckRepository = priceCheckRepository;
        this.compositePriceFetcher = compositePriceFetcher;
        this.notifier = notifier;
        this.priceChangeSummaryService = priceChangeSummaryService;
    }

    @Transactional
    public void runChecksForActiveProducts() {
        long startNanos = System.nanoTime();
        String runId = UUID.randomUUID().toString();
        MDC.put("runId", runId);
        int attempted = 0;
        int success = 0;
        int failure = 0;
        int alerted = 0;
        try {
            List<MonitoredProduct> products = productRepository.findByActiveTrueOrderByIdAsc();
            attempted = products.size();
            MDC.put("event", "price.check.start");
            log.info("productCount={}", attempted);
            MDC.remove("event");

            for (MonitoredProduct product : products) {
                MDC.put("productId", String.valueOf(product.getId()));
                MDC.put("amazonUrl", truncateForMdc(product.getAmazonUrl()));
                try {
                    ProductOutcome outcome = checkSingleProduct(product);
                    if (outcome.scrapeSuccess()) {
                        success++;
                    }
                    if (outcome.scrapeFailed()) {
                        failure++;
                    }
                    if (outcome.alerted()) {
                        alerted++;
                    }
                } catch (RuntimeException ex) {
                    failure++;
                    MDC.put("event", "price.check.failure");
                    MDC.put("cause", "unexpected");
                    log.error("Unexpected failure while checking product", ex);
                    MDC.remove("event");
                    MDC.remove("cause");
                    persistFailure(product, FetchMethod.FAILED, truncate(ex.getMessage(), 1900));
                } finally {
                    MDC.remove("productId");
                    MDC.remove("amazonUrl");
                }
            }
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            MDC.put("event", "price.check.run.summary");
            MDC.put("duration_ms", String.valueOf(durationMs));
            MDC.put("attempted", String.valueOf(attempted));
            MDC.put("success", String.valueOf(success));
            MDC.put("failure", String.valueOf(failure));
            MDC.put("alerted", String.valueOf(alerted));
            log.info("price check run finished");
            MDC.remove("event");
            MDC.remove("duration_ms");
            MDC.remove("attempted");
            MDC.remove("success");
            MDC.remove("failure");
            MDC.remove("alerted");
            MDC.remove("runId");
        }
    }

    private ProductOutcome checkSingleProduct(MonitoredProduct product) {
        Optional<BigDecimal> priorSuccessfulPrice = priceCheckRepository
                .findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(product.getId())
                .map(PriceCheck::getPriceAmount);

        Optional<PriceQuote> quote = compositePriceFetcher.fetchWithFallback(product.getAmazonUrl());
        if (quote.isEmpty()) {
            MDC.put("event", "price.check.failure");
            MDC.put("cause", "both_fetchers_empty");
            log.warn("Jsoup and AlterLab both failed to return a price");
            MDC.remove("cause");
            MDC.remove("event");
            persistFailure(
                    product,
                    FetchMethod.FAILED,
                    "Jsoup and AlterLab both failed to return a price");
            return new ProductOutcome(false, true, false);
        }

        PriceQuote resolved = quote.get();
        persistSuccess(product, resolved);

        if (priorSuccessfulPrice.isEmpty()) {
            logPriceCheckSuccess(resolved, false, Optional.empty(), Optional.empty());
            return new ProductOutcome(true, false, false);
        }
        BigDecimal previous = priorSuccessfulPrice.get();
        BigDecimal newPrice = resolved.amount();
        if (previous.compareTo(BigDecimal.ZERO) <= 0 || newPrice.compareTo(previous) >= 0) {
            logPriceCheckSuccess(resolved, false, Optional.empty(), Optional.empty());
            return new ProductOutcome(true, false, false);
        }
        BigDecimal dropPercent = previous
                .subtract(newPrice)
                .divide(previous, DROP_MATH)
                .multiply(BigDecimal.valueOf(100), DROP_MATH);
        BigDecimal dropAmount = previous.subtract(newPrice);

        boolean pctTriggered =
                product.getThresholdPct() != null && dropPercent.compareTo(product.getThresholdPct()) >= 0;
        boolean absTriggered = product.getThresholdAmount() != null
                && dropAmount.compareTo(product.getThresholdAmount()) >= 0;
        if (!pctTriggered && !absTriggered) {
            logPriceCheckSuccess(
                    resolved,
                    false,
                    Optional.of(dropPercent.setScale(2, RoundingMode.HALF_UP)),
                    Optional.of(dropAmount.setScale(2, RoundingMode.HALF_UP)));
            return new ProductOutcome(true, false, false);
        }
        String triggerTag = buildThresholdTriggerTag(pctTriggered, absTriggered);
        BigDecimal dropPctScaled = dropPercent.setScale(2, RoundingMode.HALF_UP);
        BigDecimal dropAmtScaled = dropAmount.setScale(2, RoundingMode.HALF_UP);
        logPriceCheckSuccess(resolved, true, Optional.of(dropPctScaled), Optional.of(dropAmtScaled));
        String aiSummary = generateSummarySafely(product);
        notifier.notifyPriceDrop(
                product,
                previous,
                newPrice,
                dropPctScaled,
                dropAmtScaled,
                triggerTag,
                resolved.method(),
                aiSummary);
        return new ProductOutcome(true, false, true);
    }

    /**
     * Summary generation must never block or break the alert path. The summary service
     * already returns a deterministic fallback on any internal error; this is a final
     * belt-and-braces catch in case anything truly unexpected escapes.
     */
    private String generateSummarySafely(MonitoredProduct product) {
        try {
            return priceChangeSummaryService.summarizeLast7Days(product);
        } catch (RuntimeException ex) {
            MDC.put("event", "ai.summary.skipped");
            MDC.put("reason", "unexpected");
            try {
                log.warn("Skipping AI summary for productId={}: {}", product.getId(), ex.toString());
            } finally {
                MDC.remove("event");
                MDC.remove("reason");
            }
            return null;
        }
    }

    private void logPriceCheckSuccess(
            PriceQuote resolved,
            boolean alerted,
            Optional<BigDecimal> dropPct,
            Optional<BigDecimal> dropAmount) {
        MDC.put("event", "price.check.success");
        MDC.put("fetchMethod", resolved.method().name());
        MDC.put("price", resolved.amount().toPlainString());
        MDC.put("currency", resolved.currency());
        MDC.put("alerted", Boolean.toString(alerted));
        dropPct.ifPresent(v -> MDC.put("dropPct", v.toPlainString()));
        dropAmount.ifPresent(v -> MDC.put("dropAmount", v.toPlainString()));
        try {
            log.info("successful price check persisted");
        } finally {
            MDC.remove("event");
            MDC.remove("fetchMethod");
            MDC.remove("price");
            MDC.remove("currency");
            MDC.remove("alerted");
            MDC.remove("dropPct");
            MDC.remove("dropAmount");
        }
    }

    private static String truncateForMdc(String url) {
        if (url == null) {
            return "";
        }
        return url.length() <= MAX_AMAZON_URL_MDC ? url : url.substring(0, MAX_AMAZON_URL_MDC);
    }

    private record ProductOutcome(boolean scrapeSuccess, boolean scrapeFailed, boolean alerted) {}

    private static String buildThresholdTriggerTag(boolean pctTriggered, boolean absTriggered) {
        List<String> parts = new ArrayList<>(2);
        if (pctTriggered) {
            parts.add("PCT");
        }
        if (absTriggered) {
            parts.add("ABS");
        }
        return String.join("+", parts);
    }

    private void persistSuccess(MonitoredProduct product, PriceQuote quote) {
        PriceCheck row = new PriceCheck();
        row.setProduct(product);
        row.setSuccess(true);
        row.setPriceAmount(quote.amount());
        row.setCurrency(quote.currency());
        row.setFetchMethod(quote.method());
        priceCheckRepository.save(row);
    }

    private void persistFailure(MonitoredProduct product, FetchMethod method, String message) {
        PriceCheck row = new PriceCheck();
        row.setProduct(product);
        row.setSuccess(false);
        row.setFetchMethod(method);
        row.setErrorMessage(message);
        priceCheckRepository.save(row);
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
