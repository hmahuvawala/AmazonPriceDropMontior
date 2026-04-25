# Monitoring & alerts

## Scheduler

[PriceCheckScheduler](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java) — programmatic fixed-delay chain on a dedicated [`priceCheckTaskScheduler`](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java) `ThreadPoolTaskScheduler` (pool size 1).

- `@ConditionalOnProperty(prefix="app.scheduler", name="enabled", havingValue="true", matchIfMissing=true)` — bean is omitted entirely when disabled, so `runChecksForActiveProducts` runs only via the admin endpoint in tests.
- **First run:** `ApplicationReadyEvent` schedules the first execution **60s** after startup (`BOOT_INITIAL_DELAY_MS` — not configurable via env or UI).
- **Subsequent runs:** after each run finishes, the next execution is scheduled after `SchedulerSettingsService.getCheckIntervalMs()` from the **`scheduler_settings`** table (defaults per Flyway V3; editable via `PUT /api/admin/scheduler-settings` and the SPA **Checks every … min** control). Fixed-delay semantics: long scrapes push the next start out.

## [PriceMonitoringService](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java)

Single transactional entry point: `runChecksForActiveProducts()` (annotated `@Transactional`).

Per-product flow inside `checkSingleProduct`:
1. Read previous **successful** price via `findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc`.
2. `compositePriceFetcher.fetchWithFallback(amazonUrl)`.
3. If empty → persist `success=false, fetchMethod=FAILED, errorMessage="Jsoup and AlterLab both failed to return a price"` and return.
4. Otherwise → persist `success=true` row with quote's amount/currency/method.
5. If no prior success exists → return (first observation, nothing to compare).
6. If `previous ≤ 0` or `newPrice ≥ previous` → return (no drop).
7. `dropPercent = (previous - new) / previous * 100` and `dropAmount = previous - new` using `MathContext(8, HALF_UP)` for the percent path.
8. Let `pctTriggered = (thresholdPct != null && dropPercent ≥ thresholdPct)` and `absTriggered = (thresholdAmount != null && dropAmount ≥ thresholdAmount)`. If **neither** is true → return.
9. Build the **AI-assisted 7-day summary** via [`PriceChangeSummaryService.summarizeLast7Days`](../src/main/java/com/amazonpricemonitor/service/ai/PriceChangeSummaryService.java) (always returns a non-blank string; see [AI-assisted change summary](#ai-assisted-change-summary)).
10. Otherwise call [`Notifier.notifyPriceDrop`](../src/main/java/com/amazonpricemonitor/service/notify/Notifier.java) on the active implementation with `dropAmount`, `dropPercent`, a short tag (`PCT`, `ABS`, or `PCT+ABS`) indicating which threshold(s) fired (**OR** semantics: either condition can fire the alert alone), and the summary string.

Outer loop catches `RuntimeException` from `checkSingleProduct`, logs at ERROR, and persists a `FAILED` row with the truncated (≤1900 char) exception message via `persistFailure`.

### Subtleties
- Whole call runs in **one transaction**. If a downstream throws something not caught (it is `catch RuntimeException`, so anything not subclassing it would escape — none of the current code throws checked exceptions out of `runChecksForActiveProducts`), all writes roll back.
- The notifier call happens **inside** the transaction. With `NOTIFICATION_TYPE=slack`, a slow webhook holds the DB transaction open. `RestClientException` is caught inside `SlackNotifier` so it won't roll back the transaction.
- Drop math uses `MathContext`, but the value compared to `thresholdPct` is **not** scale-normalized before `compareTo` — it works because `BigDecimal.compareTo` is scale-insensitive.
- A drop of *exactly* `thresholdPct` or *exactly* `thresholdAmount` triggers the alert (`compareTo` uses `>=` on both branches).
- New product / first run: no alert is ever sent on the first successful price. You get an alert only on the second-or-later success.
- Failed checks do not reset the comparison baseline — drop is always vs the most recent **success**, even if many failures intervened.

## Notifications (`app.notification.type`)

Exactly **one** [`Notifier`](../src/main/java/com/amazonpricemonitor/service/notify/Notifier.java) bean is active, selected by `NOTIFICATION_TYPE` / `app.notification.type`:

| Type | Bean | Behavior |
|------|------|------------|
| `log` (default) | [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java) | **INFO** `notification.sent` (MDC `channel=log` + prices/drops/triggers/method). No outbound HTTP. |
| `slack` | [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java) | POST `{"text":"..."}` to `app.notification.slack.webhook-url` (env `SLACK_WEBHOOK_URL`). **INFO** `notification.sent` on HTTP success; **WARN** `notification.failed` on errors or if the webhook URL is blank (`reason=webhook_not_configured`). |
| `noop` | [NoopNotifier](../src/main/java/com/amazonpricemonitor/service/notify/NoopNotifier.java) | Swallows alerts (tests / explicit off) — no notifier log lines. |

### [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java)

- Uses the shared `RestClient.Builder` from `AppConfiguration` (no custom timeouts — JDK defaults apply).
- Message includes prev → new prices, **percent and absolute** drop, which threshold(s) tripped (`PCT` / `ABS` / `PCT+ABS`), fetch method, a `<url|Open listing>` link, and (when non-blank) a `_7-day summary:_ …` line generated by [`PriceChangeSummaryService`](../src/main/java/com/amazonpricemonitor/service/ai/PriceChangeSummaryService.java).
- Currency is always rendered as `"USD"` in the Slack body regardless of `quote.currency()` — the persisted row keeps the actual currency.
- `escapeSlack` only handles `&`, `<`, `>`; the AI summary string is run through it before being appended so a misbehaving model output cannot inject Slack mrkdwn link/control tokens. URLs in the link slot are not escaped — fine for Amazon URLs but a hostile `displayName` could not break out (escaped), though a malicious URL could.
- Body is `{"text": "..."}` — plain text webhook payload, not blocks.
- POST failure is caught (`RestClientException`) and logged as **WARN** `notification.failed` (`channel=slack`, `reason=http_error`); the price-check row stays committed.

## AI-assisted change summary

Threshold-breach notifications include a 1–2 sentence narrative of the trailing 7 days. Implementation lives in [`service/ai/`](../src/main/java/com/amazonpricemonitor/service/ai):

| File | Responsibility |
|------|----------------|
| [`GeminiProperties`](../src/main/java/com/amazonpricemonitor/service/ai/GeminiProperties.java) | Typed `app.ai.gemini.*` config (`enabled`, `apiKey`, `model`, `baseUrl`, `timeoutMs`, `maxOutputTokens`). |
| [`PriceTrendStats`](../src/main/java/com/amazonpricemonitor/service/ai/PriceTrendStats.java) | Immutable record carrying the deterministic numbers passed to the LLM. |
| [`GeminiClient`](../src/main/java/com/amazonpricemonitor/service/ai/GeminiClient.java) | REST adapter for `generateContent`. Bounded by a hard connect+read timeout (`SimpleClientHttpRequestFactory`); never throws — failures are logged with `event=gemini.call.failure` and yield `Optional.empty()`. The configured `RestClient` is built in [`AppConfiguration#geminiRestClient`](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java) so timeouts apply once and the shared `RestClient.Builder` (used by Slack) is not mutated. |
| [`PriceChangeSummaryService`](../src/main/java/com/amazonpricemonitor/service/ai/PriceChangeSummaryService.java) | Pulls the last-7-days `price_check` rows via `findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc`, computes `PriceTrendStats` in pure Java, calls `GeminiClient`, sanitizes/caps the response, and renders a deterministic fallback when Gemini is unavailable, off, blocked, or returns empty/blank text. |

### Accuracy strategy
- **Numbers come from the DB, not the LLM.** All values (oldest, latest, min, max, avg, abs/pct change, success/failure counts) are computed from `price_check` and serialized into a JSON payload. The LLM only narrates them.
- **Strict system prompt.** "Use ONLY the numbers in the provided JSON. Do not compute, infer, or invent values. 1–2 short sentences, ≤60 words, no markdown, no URLs, no emojis."
- **Low variance.** `temperature=0.2`, `topP=0.8`, `maxOutputTokens=120` — keeps output grounded.
- **Deterministic fallback always available.** If Gemini is disabled, returns no candidates, fails the call, or returns blank text, the notifier ships a template-rendered sentence built from the same `PriceTrendStats`. The alert path therefore never blocks on AI and never silently drops the summary.
- **Output sanitization.** Control characters stripped; capped to 280 chars before being passed to the notifier.

### Failure / disablement modes (logged events)
- `event=gemini.call.success` — Gemini produced text; `latencyMs` recorded.
- `event=gemini.call.failure`, `reason=http_error` | `empty_candidates` | `unexpected` — non-fatal; deterministic fallback fires.
- `event=ai.summary.skipped`, `reason=prompt_serialization_failed` | `unexpected` — defensive catch in `PriceChangeSummaryService` / `PriceMonitoringService`; "7-day summary unavailable." is sent so the alert still ships.

### Privacy / data leaving the process
- Each Gemini call sends `displayName` (for nicer prose), the trailing-7-days numeric stats, and currency. **No URLs, ASINs, or user identifiers are sent.** Gate via `GEMINI_ENABLED=false` (or omitting `GEMINI_API_KEY`) to keep the alert path fully local; the deterministic fallback covers that case.

## Observability

### Log format

- [logback-spring.xml](../src/main/resources/logback-spring.xml): **non-`test`** profiles use **JSON** lines (`net.logstash.logback:logstash-logback-encoder`) so each log event is one object (message, level, logger, stack trace, **MDC** map). The **`test`** profile uses a **plain pattern** layout for readable surefire output.
- **Trade-off:** JSON improves aggregation (`jq`, Loki, ELK); raw `tail -f` without tooling is harder than plain text.

### MDC keys (price-check run)

[PriceMonitoringService.runChecksForActiveProducts](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java) sets:

| Key | When |
|-----|------|
| `runId` | Whole orchestrated run (UUID). |
| `productId` | Per active product iteration. |
| `amazonUrl` | Per product; truncated to 512 chars for field size. |
| `event` | See catalog below (cleared after each structured line where applicable). |
| `duration_ms`, `attempted`, `success`, `failure`, `alerted` | Only on the final **`price.check.run.summary`** line. |

Single-threaded scheduler + synchronous `runChecksForActiveProducts` keeps MDC contention negligible. If concurrent runs are introduced later, use per-task MDC inheritance/cleanup.

### Event catalog (`MDC.event`)

| Level | `event` | Meaning |
|-------|---------|---------|
| INFO | `price.check.start` | Run begins; message includes `productCount`. |
| WARN | `price.check.failure` | `cause=both_fetchers_empty` before persisting a `FAILED` row when both fetchers return empty; or `cause=unexpected` + ERROR stack when an uncaught `RuntimeException` escapes per-product logic. |
| INFO | `price.check.success` | Successful scrape persisted; MDC includes `fetchMethod`, `price`, `currency`, `alerted`, and `dropPct` / `dropAmount` when a drop vs prior success was computed. |
| INFO | `price.check.run.summary` | End of run: `duration_ms`, `attempted`, `success`, `failure`, `alerted` (successful scrapes vs fetch/other failures vs notifier invocations). |
| INFO | `notification.sent` | [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java) (MDC includes `aiSummary` when present) / successful [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java) POST (`channel=log` or `slack`). |
| WARN | `notification.failed` | Slack not configured (`reason=webhook_not_configured`) or HTTP delivery error (`reason=http_error`). |
| INFO | `gemini.call.success` | Gemini returned usable text for the 7-day summary; `latencyMs` recorded. |
| WARN | `gemini.call.failure` | Gemini call failed: `reason=http_error` (4xx/5xx/transport), `empty_candidates`, or `unexpected`. Always non-fatal — deterministic fallback ships. |
| WARN | `ai.summary.skipped` | Prompt serialization or unexpected exception in `PriceChangeSummaryService` / `PriceMonitoringService`; minimal fallback string is sent so the alert still goes out. |

Fetcher diagnostics remain mostly **DEBUG** ([CompositePriceFetcher](../src/main/java/com/amazonpricemonitor/service/CompositePriceFetcher.java), Jsoup, AlterLab) unless levels are raised — the WARN above for `both_fetchers_empty` is the orchestrator-level signal called for in the requirements.

Other useful lines:

- INFO `Starting/Finished scheduled Amazon price checks` — [PriceCheckScheduler](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java) bracket.
- WARN `AlterLab request failed ...`, `AlterLab API key is not configured ...` — client-level.
- ERROR with stack — unexpected per-product failure (also `price.check.failure` / `unexpected` in MDC).

### Spring Boot Actuator

- `GET /actuator/health` — includes datasource status when the DB is reachable.
- `GET /actuator/info` — basic app info (defaults). Exposure is limited to `health` and `info` in [application.yml](../src/main/resources/application.yml); do not enable sensitive endpoints on a public interface without auth.
