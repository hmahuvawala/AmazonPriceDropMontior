package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.MonitoredProductRepository;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(PriceMonitoringService.class);

    private static final MathContext DROP_MATH = new MathContext(8, RoundingMode.HALF_UP);

    private final MonitoredProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;
    private final CompositePriceFetcher compositePriceFetcher;
    private final SlackNotificationService slackNotificationService;

    public PriceMonitoringService(
            MonitoredProductRepository productRepository,
            PriceCheckRepository priceCheckRepository,
            CompositePriceFetcher compositePriceFetcher,
            SlackNotificationService slackNotificationService) {
        this.productRepository = productRepository;
        this.priceCheckRepository = priceCheckRepository;
        this.compositePriceFetcher = compositePriceFetcher;
        this.slackNotificationService = slackNotificationService;
    }

    @Transactional
    public void runChecksForActiveProducts() {
        List<MonitoredProduct> products = productRepository.findByActiveTrueOrderByIdAsc();
        for (MonitoredProduct product : products) {
            try {
                checkSingleProduct(product);
            } catch (RuntimeException ex) {
                log.error("Unexpected failure while checking productId={}", product.getId(), ex);
                persistFailure(product, FetchMethod.FAILED, truncate(ex.getMessage(), 1900));
            }
        }
    }

    private void checkSingleProduct(MonitoredProduct product) {
        Optional<BigDecimal> priorSuccessfulPrice = priceCheckRepository
                .findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(product.getId())
                .map(PriceCheck::getPriceAmount);

        Optional<PriceQuote> quote = compositePriceFetcher.fetchWithFallback(product.getAmazonUrl());
        if (quote.isEmpty()) {
            persistFailure(
                    product,
                    FetchMethod.FAILED,
                    "Jsoup and AlterLab both failed to return a price");
            return;
        }

        PriceQuote resolved = quote.get();
        persistSuccess(product, resolved);

        if (priorSuccessfulPrice.isEmpty()) {
            return;
        }
        BigDecimal previous = priorSuccessfulPrice.get();
        BigDecimal newPrice = resolved.amount();
        if (previous.compareTo(BigDecimal.ZERO) <= 0 || newPrice.compareTo(previous) >= 0) {
            return;
        }
        BigDecimal dropPercent = previous
                .subtract(newPrice)
                .divide(previous, DROP_MATH)
                .multiply(BigDecimal.valueOf(100), DROP_MATH);
        if (dropPercent.compareTo(product.getThresholdPct()) < 0) {
            return;
        }
        slackNotificationService.notifyPriceDrop(
                product,
                previous,
                newPrice,
                dropPercent.setScale(2, RoundingMode.HALF_UP),
                resolved.method());
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
