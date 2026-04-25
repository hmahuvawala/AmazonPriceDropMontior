package com.amazonpricemonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.MonitoredProductRepository;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.amazonpricemonitor.service.ai.PriceChangeSummaryService;
import com.amazonpricemonitor.service.notify.Notifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceMonitoringServiceTest {

    @Mock
    private MonitoredProductRepository productRepository;

    @Mock
    private PriceCheckRepository priceCheckRepository;

    @Mock
    private CompositePriceFetcher compositePriceFetcher;

    @Mock
    private Notifier notifier;

    @Mock
    private PriceChangeSummaryService priceChangeSummaryService;

    @InjectMocks
    private PriceMonitoringService priceMonitoringService;

    @Mock
    private MonitoredProduct product;

    @BeforeEach
    void wireProduct() {
        when(product.getId()).thenReturn(1L);
        when(product.getAmazonUrl()).thenReturn("https://amazon.test/dp/x");
        lenient().when(product.getThresholdPct()).thenReturn(new BigDecimal("10"));
        lenient().when(product.getThresholdAmount()).thenReturn(null);
        lenient().when(priceChangeSummaryService.summarizeLast7Days(any())).thenReturn("stub-summary");
        when(productRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(product));
    }

    @Test
    void firstSuccessfulPriceDoesNotNotify() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("99.99"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier, never())
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void priceUpDoesNotNotify() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("110.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier, never())
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void priceUnchangedDoesNotNotify() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("100.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier, never())
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void dropBelowThresholdDoesNotNotify() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("95.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier, never())
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void dropExactlyAtPctThresholdNotifiesOnce() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("90.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier).notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void dropAboveThresholdPassesScaledDropPctToNotifier() {
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("80.123"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        ArgumentCaptor<BigDecimal> dropPctCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(notifier)
                .notifyPriceDrop(
                        eq(product),
                        any(),
                        any(),
                        dropPctCaptor.capture(),
                        any(),
                        anyString(),
                        eq(FetchMethod.JSOUP),
                        any());
        assertThat(dropPctCaptor.getValue().scale()).isEqualTo(2);
        assertThat(dropPctCaptor.getValue()).isEqualTo(new BigDecimal("19.88"));
    }

    @Test
    void absoluteThresholdOnlyFiresWhenPctDoesNot() {
        when(product.getThresholdPct()).thenReturn(new BigDecimal("50"));
        when(product.getThresholdAmount()).thenReturn(new BigDecimal("5.00"));
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("80.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier).notifyPriceDrop(any(), any(), any(), any(), any(), eq("ABS"), any(), any());
    }

    @Test
    void pctFalseAbsTrueStillFiresOrSemantics() {
        when(product.getThresholdPct()).thenReturn(new BigDecimal("25"));
        when(product.getThresholdAmount()).thenReturn(new BigDecimal("5.00"));
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("92.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier).notifyPriceDrop(any(), any(), any(), any(), any(), eq("ABS"), any(), any());
    }

    @Test
    void bothThresholdsTrippedUsesCombinedTag() {
        when(product.getThresholdPct()).thenReturn(new BigDecimal("5"));
        when(product.getThresholdAmount()).thenReturn(new BigDecimal("5.00"));
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("80.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier).notifyPriceDrop(any(), any(), any(), any(), any(), eq("PCT+ABS"), any(), any());
    }

    @Test
    void aiSummaryIsForwardedToNotifierOnAlert() {
        when(priceChangeSummaryService.summarizeLast7Days(product))
                .thenReturn("Down 10% over the last week.");
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("90.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier)
                .notifyPriceDrop(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyString(),
                        any(),
                        summaryCaptor.capture());
        assertThat(summaryCaptor.getValue()).isEqualTo("Down 10% over the last week.");
    }

    @Test
    void summaryServiceFailureFallsBackGracefullyAndStillNotifies() {
        when(priceChangeSummaryService.summarizeLast7Days(product))
                .thenThrow(new RuntimeException("ai down"));
        when(priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(successCheck(new BigDecimal("100.00"))));
        when(compositePriceFetcher.fetchWithFallback(product.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("90.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier)
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    private static PriceCheck successCheck(BigDecimal amount) {
        PriceCheck pc = new PriceCheck();
        pc.setSuccess(true);
        pc.setPriceAmount(amount.setScale(2, RoundingMode.HALF_UP));
        pc.setCurrency("USD");
        pc.setFetchMethod(FetchMethod.JSOUP);
        return pc;
    }
}
