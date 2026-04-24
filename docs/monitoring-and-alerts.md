# Monitoring & alerts

## Scheduler

[PriceCheckScheduler](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java) — single bean, single `@Scheduled` method.

- `@ConditionalOnProperty(prefix="app.scheduler", name="enabled", havingValue="true", matchIfMissing=true)` — bean is omitted entirely when disabled, so `runChecksForActiveProducts` runs only via the admin endpoint in tests.
- `@Scheduled(fixedDelayString = "${app.scheduler.interval-ms:3600000}", initialDelayString = "60000")` — first run 60s after boot, then 1h after each completion (fixed-delay, not fixed-rate).
- The application class is `@EnableScheduling`. No custom `TaskScheduler` is configured — Spring's single-thread default scheduler is used. Long fetcher calls block the next run.

## [PriceMonitoringService](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java)

Single transactional entry point: `runChecksForActiveProducts()` (annotated `@Transactional`).

Per-product flow inside `checkSingleProduct`:
1. Read previous **successful** price via `findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc`.
2. `compositePriceFetcher.fetchWithFallback(amazonUrl)`.
3. If empty → persist `success=false, fetchMethod=FAILED, errorMessage="Jsoup and AlterLab both failed to return a price"` and return.
4. Otherwise → persist `success=true` row with quote's amount/currency/method.
5. If no prior success exists → return (first observation, nothing to compare).
6. If `previous ≤ 0` or `newPrice ≥ previous` → return (no drop).
7. `dropPercent = (previous - new) / previous * 100` using `MathContext(8, HALF_UP)`.
8. If `dropPercent < thresholdPct` → return.
9. Otherwise call `slackNotificationService.notifyPriceDrop(product, previous, newPrice, dropPercent.setScale(2, HALF_UP), method)`.

Outer loop catches `RuntimeException` from `checkSingleProduct`, logs at ERROR, and persists a `FAILED` row with the truncated (≤1900 char) exception message via `persistFailure`.

### Subtleties
- Whole call runs in **one transaction**. If a downstream throws something not caught (it is `catch RuntimeException`, so anything not subclassing it would escape — none of the current code throws checked exceptions out of `runChecksForActiveProducts`), all writes roll back.
- The Slack call happens **inside** the transaction. A slow webhook holds the DB transaction open. `RestClientException` is caught inside `SlackNotificationService` so it won't roll back the transaction.
- Drop math uses `MathContext`, but the value compared to `thresholdPct` is **not** scale-normalized before `compareTo` — it works because `BigDecimal.compareTo` is scale-insensitive.
- A drop of *exactly* `thresholdPct` triggers the alert (`< 0` check, not `≤ 0`). README says "≥".
- New product / first run: no alert is ever sent on the first successful price. You get an alert only on the second-or-later success.
- Failed checks do not reset the comparison baseline — drop is always vs the most recent **success**, even if many failures intervened.

## [SlackNotificationService](../src/main/java/com/amazonpricemonitor/service/SlackNotificationService.java)

- No-op if `slack.webhook-url` is blank.
- Uses the shared `RestClient.Builder` from `AppConfiguration` (no custom timeouts — JDK defaults apply).
- Builds a single-line message with `:moneybag:`, label, prev → new prices, drop %, fetch method, and a `<url|Open listing>` link.
- Currency is always rendered as `"USD"` regardless of `quote.currency()` — only the Slack message hard-codes this; the persisted row keeps the actual currency.
- `escapeSlack` only handles `&`, `<`, `>`. URLs in the link slot are not escaped — fine for Amazon URLs but a hostile `displayName` could not break out (escaped) but a malicious URL could.
- Body is `{"text": "..."}` — plain text webhook payload, not blocks.
- POST failure is caught (`RestClientException`) and logged WARN; the price-check row stays committed.

## Observability

- All logs go through SLF4J. Default level for `com.amazonpricemonitor` is `INFO`.
- Useful messages:
  - INFO `Starting/Finished scheduled Amazon price checks` — scheduler bracket.
  - DEBUG `Price resolved via Jsoup|AlterLab for url=...` — winner per product (raise to DEBUG to surface).
  - DEBUG `Jsoup could not locate a price for url=...` and `Jsoup fetch failed for url=...` — silent miss vs HTTP error.
  - WARN `AlterLab request failed ...`, `AlterLab API key is not configured ...`, `Slack webhook delivery failed ...`.
  - ERROR `Unexpected failure while checking productId=...` — anything that escapes `checkSingleProduct`.
- No metrics, no tracing, no actuator endpoints (the dependency isn't added).
