package com.amazonpricemonitor.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompositePriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(CompositePriceFetcher.class);

    private final JsoupAmazonPriceFetcher jsoupFetcher;
    private final AlterLabPriceClient alterLabPriceClient;

    public CompositePriceFetcher(JsoupAmazonPriceFetcher jsoupFetcher, AlterLabPriceClient alterLabPriceClient) {
        this.jsoupFetcher = jsoupFetcher;
        this.alterLabPriceClient = alterLabPriceClient;
    }

    public Optional<PriceQuote> fetchWithFallback(String productUrl) {
        Optional<PriceQuote> jsoup = jsoupFetcher.fetch(productUrl);
        if (jsoup.isPresent()) {
            log.debug("Price resolved via Jsoup for url={}", productUrl);
            return jsoup;
        }
        Optional<PriceQuote> alterLab = alterLabPriceClient.fetch(productUrl);
        if (alterLab.isPresent()) {
            log.debug("Price resolved via AlterLab for url={}", productUrl);
            return alterLab;
        }
        return Optional.empty();
    }
}
