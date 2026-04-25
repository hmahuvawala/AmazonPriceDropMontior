package com.amazonpricemonitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Same package as {@link MoneyParsing} (package-private API).
 *
 * <p>EU-style decimals: {@code fromPlainText} replaces {@code ','} with {@code '.'} before stripping
 * non-numeric characters, so {@code "19,99"} becomes {@code 19.99} (comma as decimal separator).
 */
class MoneyParsingTest {

    @Nested
    class FromPlainText {

        @Test
        void usCurrencyWithoutThousandsComma() {
            assertThat(MoneyParsing.fromPlainText("$1299.95")).contains(new BigDecimal("1299.95"));
        }

        @Test
        @DisplayName(
                "Thousands comma in $1,299.95: fromPlainText yields empty (comma→dot creates invalid multi-dot); firstNumberIn recovers 1299.95")
        void thousandsCommaDocumentedBehavior() {
            assertThat(MoneyParsing.fromPlainText("$1,299.95")).isEmpty();
            assertThat(MoneyParsing.firstNumberIn("$1,299.95")).contains(new BigDecimal("1299.95"));
        }

        @Test
        void currencyPrefix() {
            assertThat(MoneyParsing.fromPlainText("USD 19")).contains(new BigDecimal("19"));
        }

        @Test
        void blankAndNull() {
            assertThat(MoneyParsing.fromPlainText("")).isEmpty();
            assertThat(MoneyParsing.fromPlainText("   ")).isEmpty();
            assertThat(MoneyParsing.fromPlainText(null)).isEmpty();
        }

        @Test
        void noDigits() {
            assertThat(MoneyParsing.fromPlainText("abc")).isEmpty();
        }

        @Test
        @DisplayName("EU-style '19,99' → 19.99 (comma normalized to dot before strip)")
        void euCommaDecimalSeparator() {
            assertThat(MoneyParsing.fromPlainText("19,99")).contains(new BigDecimal("19.99"));
        }
    }

    @Nested
    class FirstNumberIn {

        @Test
        void firstPriceLikeTokenInSentence() {
            assertThat(MoneyParsing.firstNumberIn("Was $1,299.95 now $999.00"))
                    .contains(new BigDecimal("1299.95"));
        }
    }
}
