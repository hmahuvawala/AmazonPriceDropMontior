package com.amazonpricemonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.MonitoredProductRepository;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.amazonpricemonitor.service.notify.Notifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PriceMonitoringServiceIntegrationTest {

    @MockBean
    private CompositePriceFetcher compositePriceFetcher;

    @MockBean
    private Notifier notifier;

    @Autowired
    private PriceMonitoringService priceMonitoringService;

    @Autowired
    private MonitoredProductRepository monitoredProductRepository;

    @Autowired
    private PriceCheckRepository priceCheckRepository;

    @BeforeEach
    void clean() {
        priceCheckRepository.deleteAll();
        monitoredProductRepository.deleteAll();
    }

    @Test
    void successfulQuotePersistsOneSuccessRow() {
        MonitoredProduct p = persistProduct("https://amazon.test/dp/success", new BigDecimal("10"));

        when(compositePriceFetcher.fetchWithFallback(p.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("100.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        List<PriceCheck> rows =
                priceCheckRepository.findByProductIdOrderByCreatedAtAsc(p.getId(), Pageable.unpaged());
        assertThat(rows).hasSize(1);
        PriceCheck row = rows.get(0);
        assertThat(row.isSuccess()).isTrue();
        assertThat(row.getFetchMethod()).isEqualTo(FetchMethod.JSOUP);
        assertThat(row.getPriceAmount()).isEqualByComparingTo("100.00");
        assertThat(row.getCurrency()).isEqualTo("USD");
        verify(notifier, never())
                .notifyPriceDrop(any(), any(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void emptyQuotePersistsFailedRowWithCanonicalMessage() {
        MonitoredProduct p = persistProduct("https://amazon.test/dp/fail", new BigDecimal("10"));

        when(compositePriceFetcher.fetchWithFallback(p.getAmazonUrl())).thenReturn(Optional.empty());

        priceMonitoringService.runChecksForActiveProducts();

        List<PriceCheck> rows =
                priceCheckRepository.findByProductIdOrderByCreatedAtAsc(p.getId(), Pageable.unpaged());
        assertThat(rows).hasSize(1);
        PriceCheck row = rows.get(0);
        assertThat(row.isSuccess()).isFalse();
        assertThat(row.getFetchMethod()).isEqualTo(FetchMethod.FAILED);
        assertThat(row.getErrorMessage()).isEqualTo("Jsoup and AlterLab both failed to return a price");
    }

    @Test
    void exceptionOnOneProductDoesNotStopOther() {
        MonitoredProduct a = persistProduct("https://amazon.test/dp/a", new BigDecimal("10"));
        MonitoredProduct b = persistProduct("https://amazon.test/dp/b", new BigDecimal("10"));

        when(compositePriceFetcher.fetchWithFallback(a.getAmazonUrl()))
                .thenThrow(new RuntimeException("boom"));
        when(compositePriceFetcher.fetchWithFallback(b.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("50.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();

        List<PriceCheck> rowsA =
                priceCheckRepository.findByProductIdOrderByCreatedAtAsc(a.getId(), Pageable.unpaged());
        assertThat(rowsA).hasSize(1);
        assertThat(rowsA.get(0).isSuccess()).isFalse();
        assertThat(rowsA.get(0).getFetchMethod()).isEqualTo(FetchMethod.FAILED);

        List<PriceCheck> rowsB =
                priceCheckRepository.findByProductIdOrderByCreatedAtAsc(b.getId(), Pageable.unpaged());
        assertThat(rowsB).hasSize(1);
        assertThat(rowsB.get(0).isSuccess()).isTrue();
    }

    @Test
    void twoRunsWithDropMeetsThresholdInvokesNotifierOnce() {
        MonitoredProduct p = persistProduct("https://amazon.test/dp/drop", new BigDecimal("10"));

        when(compositePriceFetcher.fetchWithFallback(p.getAmazonUrl()))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("100.00"), "USD", FetchMethod.JSOUP)))
                .thenReturn(Optional.of(new PriceQuote(new BigDecimal("80.00"), "USD", FetchMethod.JSOUP)));

        priceMonitoringService.runChecksForActiveProducts();
        priceMonitoringService.runChecksForActiveProducts();

        verify(notifier, times(1))
                .notifyPriceDrop(
                        eq(p),
                        argThat(b -> b.compareTo(new BigDecimal("100")) == 0),
                        argThat(b -> b.compareTo(new BigDecimal("80")) == 0),
                        argThat(b -> b.compareTo(new BigDecimal("20.00")) == 0),
                        argThat(b -> b.compareTo(new BigDecimal("20.00")) == 0),
                        eq("PCT"),
                        eq(FetchMethod.JSOUP),
                        any());
    }

    private MonitoredProduct persistProduct(String url, BigDecimal thresholdPct) {
        MonitoredProduct p = new MonitoredProduct();
        p.setAmazonUrl(url);
        p.setDisplayName("integration");
        p.setThresholdPct(thresholdPct);
        p.setActive(true);
        return monitoredProductRepository.save(p);
    }
}
