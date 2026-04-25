package com.amazonpricemonitor.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceChangeSummaryServiceTest {

    @Mock
    private PriceCheckRepository priceCheckRepository;

    @Mock
    private GeminiClient geminiClient;

    private PriceChangeSummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new PriceChangeSummaryService(
                priceCheckRepository, geminiClient, new ObjectMapper());
    }

    @Test
    void notEnoughDataShortCircuitsWithoutCallingGemini() {
        MonitoredProduct product = product("ACME Widget");
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of(success(new BigDecimal("100.00"), Instant.now())));

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result)
                .isNotBlank()
                .contains("Only 1 successful check")
                .contains("ACME Widget");
        verify(geminiClient, never()).generate(anyString(), anyString());
    }

    @Test
    void zeroChecksProducesNoHistorySummary() {
        MonitoredProduct product = product("Empty Widget");
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of());

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).contains("Not enough history");
        verify(geminiClient, never()).generate(anyString(), anyString());
    }

    @Test
    void geminiUsedWhenAvailableAndStatsAreSufficient() {
        MonitoredProduct product = product("ACME Widget");
        Instant now = Instant.now();
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of(
                        success(new BigDecimal("100.00"), now.minus(Duration.ofDays(6))),
                        success(new BigDecimal("90.00"), now)));
        when(geminiClient.generate(anyString(), anyString()))
                .thenReturn(Optional.of("Down 10% over the last 7 days for ACME Widget."));

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).isEqualTo("Down 10% over the last 7 days for ACME Widget.");
    }

    @Test
    void blankGeminiOutputFallsBackToDeterministicTemplate() {
        MonitoredProduct product = product("ACME Widget");
        Instant now = Instant.now();
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of(
                        success(new BigDecimal("100.00"), now.minus(Duration.ofDays(6))),
                        success(new BigDecimal("90.00"), now)));
        when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of("   "));

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).contains("ACME Widget").contains("-10.00%").contains("USD");
    }

    @Test
    void emptyGeminiResponseFallsBackToDeterministicTemplate() {
        MonitoredProduct product = product("ACME Widget");
        Instant now = Instant.now();
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of(
                        success(new BigDecimal("100.00"), now.minus(Duration.ofDays(6))),
                        success(new BigDecimal("80.00"), now)));
        when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.empty());

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).contains("-20.00%");
        assertThat(result).contains("from USD100.00 to USD80.00");
    }

    @Test
    void longGeminiOutputIsTrimmedAndControlCharsStripped() {
        MonitoredProduct product = product("ACME Widget");
        Instant now = Instant.now();
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenReturn(List.of(
                        success(new BigDecimal("100.00"), now.minus(Duration.ofDays(6))),
                        success(new BigDecimal("90.00"), now)));
        StringBuilder big = new StringBuilder("Trend\u0001 down");
        for (int i = 0; i < 400; i++) {
            big.append('a');
        }
        when(geminiClient.generate(anyString(), anyString())).thenReturn(Optional.of(big.toString()));

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).doesNotContain("\u0001");
        assertThat(result.length()).isLessThanOrEqualTo(280);
    }

    @Test
    void summaryServiceIsResilientToRepositoryFailures() {
        MonitoredProduct product = product("ACME Widget");
        when(priceCheckRepository
                        .findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), any()))
                .thenThrow(new RuntimeException("db down"));

        String result = summaryService.summarizeLast7Days(product);

        assertThat(result).isEqualTo("7-day summary unavailable.");
    }

    @Test
    void computeStatsAggregatesAcrossSuccessAndFailureRows() {
        Instant now = Instant.now();
        List<PriceCheck> checks = new ArrayList<>();
        checks.add(success(new BigDecimal("100.00"), now.minus(Duration.ofDays(6))));
        checks.add(failure(now.minus(Duration.ofDays(5))));
        checks.add(success(new BigDecimal("80.00"), now.minus(Duration.ofDays(2))));
        checks.add(success(new BigDecimal("90.00"), now));

        Optional<PriceTrendStats> stats = PriceChangeSummaryService.computeStats(checks);

        assertThat(stats).isPresent();
        PriceTrendStats s = stats.get();
        assertThat(s.numChecks()).isEqualTo(4);
        assertThat(s.numSuccess()).isEqualTo(3);
        assertThat(s.numFailures()).isEqualTo(1);
        assertThat(s.oldestPrice()).isEqualByComparingTo("100.00");
        assertThat(s.latestPrice()).isEqualByComparingTo("90.00");
        assertThat(s.minPrice()).isEqualByComparingTo("80.00");
        assertThat(s.maxPrice()).isEqualByComparingTo("100.00");
        assertThat(s.avgPrice()).isEqualByComparingTo("90.00");
        assertThat(s.absChange()).isEqualByComparingTo("-10.00");
        assertThat(s.pctChange()).isEqualByComparingTo("-10.00");
        assertThat(s.hasEnoughDataToNarrate()).isTrue();
    }

    private static MonitoredProduct product(String name) {
        MonitoredProduct p = new MonitoredProduct();
        p.setAmazonUrl("https://amazon.test/dp/x");
        p.setDisplayName(name);
        // The id is normally assigned by JPA on persist. Tests use this value to satisfy
        // the repository's anyLong() matcher, which (Mockito ≥ 2) does not match null.
        try {
            Field idField = MonitoredProduct.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(p, 42L);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        return p;
    }

    private static PriceCheck success(BigDecimal amount, Instant at) {
        PriceCheck pc = new PriceCheck();
        pc.setSuccess(true);
        pc.setPriceAmount(amount);
        pc.setCurrency("USD");
        pc.setFetchMethod(FetchMethod.JSOUP);
        pc.setCreatedAt(at);
        return pc;
    }

    private static PriceCheck failure(Instant at) {
        PriceCheck pc = new PriceCheck();
        pc.setSuccess(false);
        pc.setFetchMethod(FetchMethod.FAILED);
        pc.setErrorMessage("nope");
        pc.setCreatedAt(at);
        return pc;
    }
}
