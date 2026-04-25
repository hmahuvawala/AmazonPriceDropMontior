package com.amazonpricemonitor.service;

import com.amazonpricemonitor.config.JsoupClientProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsoupAmazonPriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupAmazonPriceFetcher.class);

    private static final List<String> PRICE_SELECTORS = List.of(
            "#corePrice_feature_div span.a-price .a-offscreen",
            "#corePriceDisplay_desktop_feature_div span.a-price .a-offscreen",
            "#tp_price_block_total_price_row span.a-price .a-offscreen",
            "span.a-price.a-text-price .a-offscreen",
            "span.a-price .a-offscreen",
            "#priceblock_ourprice",
            "#priceblock_dealprice",
            "input#twister-plus-price-data-price");

    private final JsoupClientProperties properties;

    public JsoupAmazonPriceFetcher(JsoupClientProperties properties) {
        this.properties = properties;
    }

    public Optional<PriceQuote> fetch(String productUrl) {
        try {
            Document document = Jsoup.connect(productUrl)
                    .userAgent(properties.getUserAgent())
                    .timeout(properties.getReadTimeoutMs())
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get();

            return parsePriceFromDocument(document);
        } catch (IOException ex) {
            log.debug("Jsoup fetch failed for url={}: {}", productUrl, ex.toString());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.debug("Jsoup parse failed for url={}: {}", productUrl, ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Parses an already-fetched document (no network). Used by tests with HTML fixtures.
     */
    Optional<PriceQuote> parsePriceFromDocument(Document document) {
        for (String selector : PRICE_SELECTORS) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String text = element.hasAttr("value") ? element.attr("value") : element.text();
            Optional<java.math.BigDecimal> parsed = MoneyParsing.fromPlainText(text);
            if (parsed.isEmpty()) {
                parsed = MoneyParsing.firstNumberIn(text);
            }
            if (parsed.isPresent() && parsed.get().signum() > 0) {
                return Optional.of(new PriceQuote(parsed.get(), "USD", FetchMethod.JSOUP));
            }
        }
        log.debug("Jsoup could not locate a price in document");
        return Optional.empty();
    }
}
