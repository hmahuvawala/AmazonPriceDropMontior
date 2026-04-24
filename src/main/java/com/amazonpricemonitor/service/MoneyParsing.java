package com.amazonpricemonitor.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MoneyParsing {

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)");

    private MoneyParsing() {}

    static Optional<BigDecimal> fromPlainText(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.replace(",", ".").replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> firstNumberIn(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(raw.replace(",", ""));
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(matcher.group(1).replace(",", ".")));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
