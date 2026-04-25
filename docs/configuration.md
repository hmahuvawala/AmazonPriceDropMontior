# Configuration

All runtime config lives in [application.yml](../src/main/resources/application.yml) and is bound to typed `@ConfigurationProperties` classes registered in [AppConfiguration](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java).

`AppConfiguration` also exposes the singleton `RestClient.Builder` bean used by [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java) (AlterLab builds its own `RestClient` with custom timeouts).

## Property → env-var mapping

| Property | Env var | Default | Used by |
|---|---|---|---|
| `spring.datasource.url` | `DB_HOST`, `DB_PORT`, `DB_NAME` | `jdbc:postgresql://localhost:5432/amazon_price_monitor` | JPA/Flyway |
| `spring.datasource.username` | `DB_USER` | `monitor` | JPA |
| `spring.datasource.password` | `DB_PASSWORD` | `monitor` | JPA |
| `server.port` | `SERVER_PORT` | `8080` | Tomcat |
| `alterlab.base-url` | `ALTERLAB_BASE_URL` | `https://api.alterlab.io/api/v1` | [AlterLabProperties](../src/main/java/com/amazonpricemonitor/config/AlterLabProperties.java) |
| `alterlab.api-key` | `ALTERLAB_API_KEY` | *(empty)* | AlterLab fallback (skipped if empty) |
| `alterlab.scrape-path` | — | `/scrape` | AlterLab |
| `alterlab.request-timeout-seconds` | `ALTERLAB_TIMEOUT_SECONDS` | `90` (floored at 30 in client) | AlterLab read timeout |
| `app.notification.type` | `NOTIFICATION_TYPE` | `log` | Selects [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java), [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java), or [NoopNotifier](../src/main/java/com/amazonpricemonitor/service/notify/NoopNotifier.java) (`log` \| `slack` \| `noop`) |
| `app.notification.slack.webhook-url` | `SLACK_WEBHOOK_URL` | *(empty)* | Slack POST when `type=slack`; if empty, [SlackNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SlackNotifier.java) skips the HTTP call |
| `app.scheduler.enabled` | `SCHEDULER_ENABLED` | `true` | gates [PriceCheckScheduler](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java) bean via `@ConditionalOnProperty` |
| `app.scheduler.interval-ms` | `SCHEDULER_INTERVAL_MS` | `3600000` (1h) | default when [SchedulerSettingsService](../src/main/java/com/amazonpricemonitor/service/SchedulerSettingsService.java) creates the singleton `scheduler_settings` row (e.g. H2 tests without Flyway). **Production** interval is stored in the DB (Flyway V3 seed + UI / `PUT /api/admin/scheduler-settings`). |
| `app.jsoup.connect-timeout-ms` | — | `10000` | [JsoupClientProperties](../src/main/java/com/amazonpricemonitor/config/JsoupClientProperties.java) |
| `app.jsoup.read-timeout-ms` | — | `15000` | Jsoup `.timeout()` |
| `app.jsoup.user-agent` | — | macOS Chrome 120 UA string | Jsoup `.userAgent()` |
| `app.ai.gemini.enabled` | `GEMINI_ENABLED` | `true` | Gates outbound Gemini calls in [GeminiClient](../src/main/java/com/amazonpricemonitor/service/ai/GeminiClient.java). When `false` (or `apiKey` blank), [PriceChangeSummaryService](../src/main/java/com/amazonpricemonitor/service/ai/PriceChangeSummaryService.java) ships its deterministic fallback summary on alerts. |
| `app.ai.gemini.api-key` | `GEMINI_API_KEY` | *(empty)* | Treated as a disable when blank. **Secret** — never commit. |
| `app.ai.gemini.model` | `GEMINI_MODEL` | `gemini-2.0-flash` | Path-segmented into the `generateContent` URL. |
| `app.ai.gemini.base-url` | `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta` | Override only for tests/proxies. |
| `app.ai.gemini.timeout-ms` | `GEMINI_TIMEOUT_MS` | `5000` | Hard upper bound for connect+read on the Gemini call (applied via `SimpleClientHttpRequestFactory` in [AppConfiguration#geminiRestClient](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java)). |
| `app.ai.gemini.max-output-tokens` | `GEMINI_MAX_OUTPUT_TOKENS` | `120` | Bounds Gemini's response length; the service additionally caps the final string at 280 chars. |
| `management.endpoints.web.exposure.include` | — | `health`, `info` | [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) HTTP exposure only for liveness-style checks and basic app metadata — no metrics/env heap in this prototype. |
| `management.endpoint.health.show-details` | — | `never` | Keeps health JSON compact; avoids leaking details without auth. |
| `logging.level.com.amazonpricemonitor` | — | `INFO` | Applied via [logback-spring.xml](../src/main/resources/logback-spring.xml) `springProperty` so package levels still work with a custom Logback file. |

The legacy top-level `slack.webhook-url` key was removed; use `app.notification.slack.webhook-url` (still populated from `SLACK_WEBHOOK_URL` in YAML).

`.env.example` mirrors the above and is what the README points users at.

## Behavior gates

- **Scheduler off**: set `SCHEDULER_ENABLED=false` (or `app.scheduler.enabled: false`). The bean is conditionally created. Manual `POST /api/admin/run-checks` still works.
- **Notifications**: `NOTIFICATION_TYPE=log` (default) logs **INFO** `notification.sent` (MDC) when a threshold fires — no secrets, no outbound HTTP. Use `slack` + `SLACK_WEBHOOK_URL` for Slack. Use `noop` to disable alerts entirely (no notifier log lines).
- **Slack with empty webhook**: with `NOTIFICATION_TYPE=slack`, an empty `SLACK_WEBHOOK_URL` skips the POST and emits **WARN** `notification.failed` with `reason=webhook_not_configured`; checks still run and persist.
- **AlterLab off**: leave `ALTERLAB_API_KEY` empty. `AlterLabPriceClient.fetch` logs WARN and returns `Optional.empty()`; if Jsoup also fails, the row is `FAILED`.
- **Gemini off**: leave `GEMINI_API_KEY` empty (or set `GEMINI_ENABLED=false`). The deterministic fallback summary still ships on every alert — no outbound HTTP, no upstream dependency on the alert path. See [Monitoring & alerts › AI-assisted change summary](monitoring-and-alerts.md#ai-assisted-change-summary).

## Subtleties

- `JsoupClientProperties.connect-timeout-ms` is **declared but not used** — the Jsoup fetcher only passes `read-timeout-ms` into `Jsoup.connect(...).timeout(...)`. Passing the same value also covers connect timeout in Jsoup's API.
- `AlterLabPriceClient` ignores `request-timeout-seconds` if it's below 30 (`Math.max(30, …)`).
- The **60s** delay before the **first** scheduled run after boot is fixed in code ([PriceCheckScheduler.BOOT_INITIAL_DELAY_MS](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java)); it is not env-driven and not editable in the UI.
- Hibernate `time_zone` is forced to UTC; `TIMESTAMPTZ` columns; `open-in-view: false` (no lazy loading after the controller returns).
- Logging level for app code is `INFO` by default; fetchers log failures at DEBUG so failed Jsoup attempts are silent unless you bump the level.

## Logging (Logback)

- [logback-spring.xml](../src/main/resources/logback-spring.xml): **`test`** profile → single-line **PatternLayout** on the console (readable `mvn test` / CI output). **Any other profile** → **Logstash JSON** one line per event (`LogstashEncoder`), including MDC keys (`event`, `runId`, `productId`, etc.) for Loki/ELK-style queries.
- **Trade-off:** default non-test logs are JSON — great for aggregation, weaker for raw `tail -f` unless piped through `jq` or a log viewer.

## Actuator

- `GET /actuator/health` — liveness; includes DB when the datasource is up (H2/Postgres).
- `GET /actuator/info` — minimal app metadata (Spring Boot defaults). **Do not** broaden exposure (`env`, `heapdump`, etc.) on the public internet without authentication.

## Test-profile overrides

[application-test.yml](../src/test/resources/application-test.yml) replaces datasource with H2 (PostgreSQL mode), disables Flyway, sets JPA to `create-drop`, sets a dummy AlterLab key, disables the scheduler, sets `app.notification.type=noop` so tests do not emit Slack traffic or notifier noise, sets `app.ai.gemini.enabled=false` (with a blank `api-key`) to keep tests deterministic and offline — they exercise the deterministic fallback path instead, and mirrors `management.endpoints.web.exposure.include` with `show-details: never` for actuator.

Regression tests use Mockito’s **subclass** mock maker via [mockito-extensions/org.mockito.plugins.MockMaker](../src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) so mocking concrete beans such as `CompositePriceFetcher` works on newer JDKs where the inline agent path is restricted.
