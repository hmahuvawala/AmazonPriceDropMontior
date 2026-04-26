package com.amazonpricemonitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonpricemonitor.config.JsoupClientProperties;
import com.amazonpricemonitor.domain.FetchMethod;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsoupAmazonPriceFetcherTest {

    private JsoupAmazonPriceFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new JsoupAmazonPriceFetcher(new JsoupClientProperties());
    }

    @Test
    void corePriceFeatureOffscreen() throws IOException {
        assertParsedAmount("core_price_feature_offscreen.html", "29.99");
    }

    @Test
    void corePriceDisplayDesktop() throws IOException {
        assertParsedAmount("core_price_display_desktop.html", "40.00");
    }

    @Test
    void tpPriceBlock() throws IOException {
        assertParsedAmount("tp_price_block.html", "55.50");
    }

    @Test
    void aTextPriceOffscreen() throws IOException {
        assertParsedAmount("a_text_price_offscreen.html", "12.34");
    }

    @Test
    void genericAPriceOffscreen() throws IOException {
        assertParsedAmount("generic_a_price_offscreen.html", "88.88");
    }

    @Test
    void priceblockOurprice() throws IOException {
        assertParsedAmount("priceblock_ourprice.html", "15.00");
    }

    @Test
    void priceblockDealprice() throws IOException {
        assertParsedAmount("priceblock_dealprice.html", "7.25");
    }

    @Test
    void twisterPlusUsesValueAttribute() throws IOException {
        assertParsedAmount("twister_plus_value.html", "42.00");
    }

    @Test
    void noMatchingNodes() throws IOException {
        Document doc = parseFixture("no_price.html");
        assertThat(fetcher.parsePriceFromDocument(doc)).isEmpty();
    }

    @Test
    void zeroPriceSkipped() throws IOException {
        Document doc = parseFixture("zero_price.html");
        assertThat(fetcher.parsePriceFromDocument(doc)).isEmpty();
    }

    @Test
    void malformedNoDigits() throws IOException {
        Document doc = parseFixture("malformed_no_digits.html");
        assertThat(fetcher.parsePriceFromDocument(doc)).isEmpty();
    }

    private void assertParsedAmount(String fixtureFile, String expectedPlain) throws IOException {
        Document doc = parseFixture(fixtureFile);
        assertThat(fetcher.parsePriceFromDocument(doc))
                .isPresent()
                .get()
                .satisfies(q -> {
                    assertThat(q.amount()).isEqualByComparingTo(new BigDecimal(expectedPlain));
                    assertThat(q.currency()).isEqualTo("USD");
                    assertThat(q.method()).isEqualTo(FetchMethod.JSOUP);
                });
    }

    private static Document parseFixture(String name) throws IOException {
        String path = "/fixtures/amazon/" + name;
        try (InputStream in = JsoupAmazonPriceFetcherTest.class.getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Jsoup.parse(html);
        }
    }
}
