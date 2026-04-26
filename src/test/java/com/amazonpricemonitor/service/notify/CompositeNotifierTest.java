package com.amazonpricemonitor.service.notify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.amazonpricemonitor.domain.FetchMethod;
import com.amazonpricemonitor.domain.MonitoredProduct;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeNotifierTest {

    @Mock
    private ChannelNotifier first;

    @Mock
    private ChannelNotifier second;

    @Test
    void invokesAllDelegates() {
        CompositeNotifier composite = new CompositeNotifier(List.of(first, second));
        MonitoredProduct product = new MonitoredProduct();
        product.setAmazonUrl("https://amazon.example/dp/X");

        composite.notifyPriceDrop(
                product,
                new BigDecimal("10"),
                new BigDecimal("8"),
                new BigDecimal("20"),
                new BigDecimal("2"),
                "pct",
                FetchMethod.JSOUP,
                "summary");

        verify(first).notifyPriceDrop(
                eq(product),
                eq(new BigDecimal("10")),
                eq(new BigDecimal("8")),
                eq(new BigDecimal("20")),
                eq(new BigDecimal("2")),
                eq("pct"),
                eq(FetchMethod.JSOUP),
                eq("summary"));
        verify(second).notifyPriceDrop(
                eq(product),
                eq(new BigDecimal("10")),
                eq(new BigDecimal("8")),
                eq(new BigDecimal("20")),
                eq(new BigDecimal("2")),
                eq("pct"),
                eq(FetchMethod.JSOUP),
                eq("summary"));
    }

    @Test
    void secondDelegateStillRunsWhenFirstThrows() {
        doThrow(new RuntimeException("boom"))
                .when(first)
                .notifyPriceDrop(any(), any(), any(), any(), any(), any(), any(), any());

        CompositeNotifier composite = new CompositeNotifier(List.of(first, second));
        MonitoredProduct product = new MonitoredProduct();
        product.setAmazonUrl("https://a.example/p");

        composite.notifyPriceDrop(
                product,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "none",
                FetchMethod.JSOUP,
                null);

        verify(second)
                .notifyPriceDrop(
                        eq(product),
                        eq(BigDecimal.ONE),
                        eq(BigDecimal.ONE),
                        eq(BigDecimal.ZERO),
                        eq(BigDecimal.ZERO),
                        eq("none"),
                        eq(FetchMethod.JSOUP),
                        eq(null));
    }

    @Test
    void emptyDelegatesIsNoOp() {
        CompositeNotifier composite = new CompositeNotifier(List.of());
        MonitoredProduct product = new MonitoredProduct();
        product.setAmazonUrl("https://x");

        Assertions.assertDoesNotThrow(() -> composite.notifyPriceDrop(
                product,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "x",
                FetchMethod.JSOUP,
                null));
    }
}
