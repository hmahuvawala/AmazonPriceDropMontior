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
- The notifier call happens **inside** the transaction. Slow SMTP or Twilio calls hold the DB transaction open; `MailException` / `RestClientException` are caught inside [EmailNotifier](../src/main/java/com/amazonpricemonitor/service/notify/EmailNotifier.java) / [SmsNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SmsNotifier.java) so they do not roll back the transaction.
- Drop math uses `MathContext`, but the value compared to `thresholdPct` is **not** scale-normalized before `compareTo` — it works because `BigDecimal.compareTo` is scale-insensitive.
- A drop of *exactly* `thresholdPct` or *exactly* `thresholdAmount` triggers the alert (`compareTo` uses `>=` on both branches).
- New product / first run: no alert is ever sent on the first successful price. You get an alert only on the second-or-later success.
- Failed checks do not reset the comparison baseline — drop is always vs the most recent **success**, even if many failures intervened.

## Notifications (multi-channel)

[`PriceMonitoringService`](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java) injects the [`Notifier`](../src/main/java/com/amazonpricemonitor/service/notify/Notifier.java) interface. The **primary** implementation is [`CompositeNotifier`](../src/main/java/com/amazonpricemonitor/service/notify/CompositeNotifier.java), which fans out to every active [`ChannelNotifier`](../src/main/java/com/amazonpricemonitor/service/notify/ChannelNotifier.java) bean. Each channel is toggled independently; one channel failing does not suppress the others (failures are **WARN** logged from the composite).

| Channel | Bean | `app.notification.*` flag | Behavior |
|---------|------|---------------------------|----------|
| Log (default on) | [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java) | `log.enabled` (`NOTIFY_LOG_ENABLED`, default `true`) | **INFO** `notification.sent` (MDC `channel=log` + prices/drops/triggers/method + optional `aiSummary`). No outbound HTTP. |
| Email | [EmailNotifier](../src/main/java/com/amazonpricemonitor/service/notify/EmailNotifier.java) | `email.enabled` (`NOTIFY_EMAIL_ENABLED`) | Plain-text `SimpleMailMessage` via `JavaMailSender` when `spring.mail.*` is configured (`MAIL_HOST`, etc.). **INFO** `notification.sent` / **WARN** `notification.failed` (`channel=email`, `reason=not_configured` \| `mail_error`). Requires non-blank `from` / `to` (`NOTIFY_EMAIL_FROM`, comma-separated `NOTIFY_EMAIL_TO`). |
| SMS (Twilio) | [SmsNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SmsNotifier.java) | `sms.enabled` (`NOTIFY_SMS_ENABLED`) | `POST …/Accounts/{sid}/Messages.json` with Basic auth (`TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN`), form body `From` / `To` / `Body`. One HTTP call per comma-separated `NOTIFY_SMS_TO` number. Hard timeout via [`AppConfiguration#twilioRestClient`](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java). **INFO** `notification.sent` / **WARN** `notification.failed` (`channel=sms`, `reason=not_configured` \| `http_error`). |

**All channels off:** if every flag is `false` and no channel beans exist, `CompositeNotifier` receives an empty delegate list and becomes a no-op (same effect as the old `noop` type).

### Message content (email + SMS)

- Body includes prev → new prices, **percent and absolute** drop, which threshold(s) tripped (`PCT` / `ABS` / `PCT+ABS`), fetch method, listing URL, and (when non-blank) a **7-day summary** block from [`PriceChangeSummaryService`](../src/main/java/com/amazonpricemonitor/service/ai/PriceChangeSummaryService.java).
- Currency is always rendered as `"USD"` in the notification text regardless of `quote.currency()` — the persisted row keeps the actual currency.
- **SMS body cap:** total body length is capped at **320 characters** (~2 GSM segments). The core price facts are preserved; the AI summary tail is truncated or omitted if needed so per-alert SMS cost stays bounded.
- **Email** ships the full summary text when present (no HTML escaping beyond what the mail stack does for plain text).

## AI-assisted change summary

Threshold-breach notifications include a 1–2 sentence narrative of the trailing 7 days. Implementation lives in [`service/ai/`](../src/main/java/com/amazonpricemonitor/service/ai):

| File | Responsibility |
|------|----------------|
| [`GeminiProperties`](../src/main/java/com/amazonpricemonitor/service/ai/GeminiProperties.java) | Typed `app.ai.gemini.*` config (`enabled`, `apiKey`, `model`, `baseUrl`, `timeoutMs`, `maxOutputTokens`). |
| [`PriceTrendStats`](../src/main/java/com/amazonpricemonitor/service/ai/PriceTrendStats.java) | Immutable record carrying the deterministic numbers passed to the LLM. |
| [`GeminiClient`](../src/main/java/com/amazonpricemonitor/service/ai/GeminiClient.java) | REST adapter for `generateContent`. Bounded by a hard connect+read timeout (`SimpleClientHttpRequestFactory`); never throws — failures are logged with `event=gemini.call.failure` and yield `Optional.empty()`. The configured `RestClient` is built in [`AppConfiguration#geminiRestClient`](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java) so timeouts apply once and the shared `RestClient.Builder` is not mutated. |
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

- [logback-spring.xml](../src/main/resources/logback-spring.xml) defines three profile-driven encoders:
  - **Default (no `test`, no `json`)** — single-line pattern `%d{HH:mm:ss.SSS} %-5level %logger{0} {%mdc} - %msg%n%ex`. Each line carries the full **MDC** map inline as `{key=value, key=value}` (collapsed to `{-}` when MDC is empty), so `event=`, `runId=`, `productId=`, etc. are scannable in `tail -f` / `docker logs`. Optimized for terminal reading.
  - **`json` profile** (opt-in via `SPRING_PROFILES_ACTIVE=json`) — `net.logstash.logback:logstash-logback-encoder` emits one JSON object per event (message, level, logger, stack trace, MDC map). Use this in prod / when shipping to Loki / ELK / Datadog.
  - **`test` profile** — plain pattern with thread + FQCN, tuned for readable Surefire output.
- **Trade-off:** the default pattern is human-friendly but lossy for parsers (commas inside MDC values, no schema). Switch to `json` for any aggregation or `jq`-driven analysis.

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
| INFO | `notification.sent` | [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java) (`channel=log`) / successful email send (`channel=email`) / successful Twilio accept (`channel=sms`). |
| WARN | `notification.failed` | Email: `reason=not_configured` \| `mail_error`. SMS: `reason=not_configured` \| `http_error`. |
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
