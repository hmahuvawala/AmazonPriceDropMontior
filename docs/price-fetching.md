# Price fetching

Two fetchers behind a composite. All return `Optional<PriceQuote>` and never throw.

```
CompositePriceFetcher.fetchWithFallback(url)
  → JsoupAmazonPriceFetcher.fetch(url)        // primary
  → AlterLabPriceClient.fetch(url)            // fallback (only if Jsoup empty)
  → Optional.empty()                          // both failed
```

[PriceQuote](../src/main/java/com/amazonpricemonitor/service/PriceQuote.java) is `record(BigDecimal amount, String currency, FetchMethod method)`.

## [JsoupAmazonPriceFetcher](../src/main/java/com/amazonpricemonitor/service/JsoupAmazonPriceFetcher.java)

- `Jsoup.connect(url).userAgent(...).timeout(readTimeoutMs).header("Accept-Language", "en-US,en;q=0.9").get()`.
- Tries selectors in this order, taking the first one that yields a positive `BigDecimal`:
  1. `#corePrice_feature_div span.a-price .a-offscreen`
  2. `#corePriceDisplay_desktop_feature_div span.a-price .a-offscreen`
  3. `#tp_price_block_total_price_row span.a-price .a-offscreen`
  4. `span.a-price.a-text-price .a-offscreen`
  5. `span.a-price .a-offscreen`
  6. `#priceblock_ourprice`
  7. `#priceblock_dealprice`
  8. `input#twister-plus-price-data-price` (read from `value` attr)
- Element value: `value` attribute if present, otherwise `.text()`.
- Parsing: `MoneyParsing.fromPlainText` first (strict strip), then `firstNumberIn` as a regex fallback.
- Returns `PriceQuote(amount, "USD", JSOUP)`. **Currency is hard-coded** — no localization.
- `IOException` (HTTP failure, timeout, blocked) and `RuntimeException` (selector/parse oddities) are caught and logged at DEBUG; returns `Optional.empty()`.

This is brittle by nature — Amazon CAPTCHAs, regional redirects, and selector churn all surface as silent empties. The AlterLab fallback is the safety net.

## [AlterLabPriceClient](../src/main/java/com/amazonpricemonitor/service/AlterLabPriceClient.java)

- Builds its own `RestClient` with a JDK `HttpClient` underneath: 30s connect timeout, read timeout = `max(30, alterlab.request-timeout-seconds)`.
- Skips and warns if `apiKey` is blank.
- Request:
  - `POST {baseUrl}{scrapePath}` (defaults: `https://api.alterlab.io/api/v1/scrape`)
  - Header `X-API-Key: <key>`
  - Body: `{"url": <productUrl>, "formats": ["json"], "sync": true}` (serialized via Jackson `ObjectMapper`)
- Response parsing — looks at `content.json`, then in order:
  - `content.json.price` (number → `decimalValue()`; string → `MoneyParsing.fromPlainText`)
  - `content.json.offers.price` (same handling)
- Currency from `content.json.currency` if textual+non-blank, else `"USD"`.
- Returns `PriceQuote(amount, currency, ALTERLAB)`.
- Any `RestClientException` or other `RuntimeException` → WARN/DEBUG log, `Optional.empty()`.
- `trimTrailingSlash` on `baseUrl` before passing to `RestClient.builder().baseUrl(...)`.

The AlterLab REST docs are referenced from `context.md` and the README ([alterlab.io/docs/api/rest](https://alterlab.io/docs/api/rest)).

## [MoneyParsing](../src/main/java/com/amazonpricemonitor/service/MoneyParsing.java)

Package-private utility, two static methods:
- `fromPlainText(raw)` — strips everything except digits and `.` (treats `,` as `.`), then `new BigDecimal(...)`. Returns empty on blank/`NumberFormatException`.
- `firstNumberIn(raw)` — regex `(\d+(?:[.,]\d{1,2})?)` over a comma-stripped copy; first match.

Edge cases worth knowing:
- `fromPlainText("$1,299.95")` → `1299.95` (commas removed). Good.
- `fromPlainText("1.299,95")` (EU) → `1.29995` because both `,` and stripping collapse. **EU formats are not handled correctly.**
- `firstNumberIn` strips commas before regex, so for `"1,299.95"` it captures `"1299.95"`. For `"1.299,95"` it captures `"1.299"`.

## Composite ordering & instrumentation

[CompositePriceFetcher](../src/main/java/com/amazonpricemonitor/service/CompositePriceFetcher.java) just chains the two `Optional`s. No retry, no circuit-breaker, no metrics. `log.debug` on each path — bump logger to DEBUG to see which fetcher won.
