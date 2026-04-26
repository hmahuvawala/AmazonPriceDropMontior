# Configuration

All runtime config lives in [application.yml](../src/main/resources/application.yml) and is bound to typed `@ConfigurationProperties` classes registered in [AppConfiguration](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java).

`AppConfiguration` exposes the singleton `RestClient.Builder` bean plus dedicated `RestClient` beans for Gemini and Twilio SMS (`SimpleClientHttpRequestFactory` timeouts). AlterLab builds its own `RestClient` with custom read timeouts.

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
| `app.admin.allow-test-notification` | `ADMIN_ALLOW_TEST_NOTIFICATION` | `false` | When `true`, enables `POST /api/admin/send-test-notification` (synthetic notifier probe; **no auth** — keep `false` in production) |
| `app.notification.log.enabled` | `NOTIFY_LOG_ENABLED` | `true` | [LogNotifier](../src/main/java/com/amazonpricemonitor/service/notify/LogNotifier.java) — structured INFO lines on alerts |
| `app.notification.email.enabled` | `NOTIFY_EMAIL_ENABLED` | `false` | [EmailNotifier](../src/main/java/com/amazonpricemonitor/service/notify/EmailNotifier.java); also needs `spring.mail.*` / `MAIL_*` so `JavaMailSender` auto-configures |
| `app.notification.email.from` | `NOTIFY_EMAIL_FROM` | *(empty)* | Sender address. **Recipients** (`to`) are no longer env-driven — they live in the DB; see [Notification recipients](#notification-recipients). |
| `app.notification.email.subject-prefix` | `NOTIFY_EMAIL_SUBJECT_PREFIX` | *(blank → default `[Amazon Price Monitor]` in code)* | Email subject prefix |
| `app.notification.sms.enabled` | `NOTIFY_SMS_ENABLED` | `false` | [SmsNotifier](../src/main/java/com/amazonpricemonitor/service/notify/SmsNotifier.java) (Twilio) |
| `app.notification.sms.*` | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM`, `TWILIO_BASE_URL`, `NOTIFY_SMS_TIMEOUT_MS` | — | Twilio REST credentials + sender. **Recipients** (`to`) are no longer env-driven — they live in the DB; see [Notification recipients](#notification-recipients). |
| `spring.mail.*` | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS_ENABLE`, `MAIL_CONNECTION_TIMEOUT_MS`, `MAIL_TIMEOUT_MS` | see [application.yml](../src/main/resources/application.yml) | Required when email alerts are enabled (`NOTIFY_EMAIL_ENABLED=true` and non-blank host) |
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

`.env.example` mirrors the above and is what the README points users at.

## Notification recipients

Recipient lists (email addresses and SMS phone numbers) are **not** env-var driven. They live in the singleton `notification_recipients` row (`id=1`), seeded empty by [Flyway V4](../src/main/resources/db/migration/V4__notification_recipients.sql) and edited via the SPA or `PUT /api/admin/notification-recipients` (see [api.md › Notification recipients](api.md#notification-recipients-json)).

- [`NotificationRecipientsService`](../src/main/java/com/amazonpricemonitor/service/NotificationRecipientsService.java) trims entries, drops blanks, validates emails against `^[^\s@]+@[^\s@]+\.[^\s@]+$` and phones as **E.164** (`^\+[1-9]\d{6,14}$`), and caps CSVs at **4000** chars (email) / **500** chars (SMS).
- For H2-based tests where Flyway is disabled, `NotificationRecipientsService.ensureDefaultRowIfMissing` creates the row on demand.
- Channels still gate on the `*.enabled` flags above; an empty recipient list short-circuits the channel with `notification.failed` (`reason=not_configured`) even when otherwise enabled.

## Behavior gates

- **Scheduler off**: set `SCHEDULER_ENABLED=false` (or `app.scheduler.enabled: false`). The bean is conditionally created. Manual `POST /api/admin/run-checks` still works.
- **Notifications**: enable any mix of **log** (default on), **email** (`NOTIFY_EMAIL_ENABLED=true` + `MAIL_*` + non-blank `NOTIFY_EMAIL_FROM` + at least one DB-stored email recipient), and **SMS** (`NOTIFY_SMS_ENABLED=true` + Twilio creds/`TWILIO_FROM` + at least one DB-stored E.164 phone). Set `NOTIFY_LOG_ENABLED=false` (and disable email/SMS) to silence all notifier output — `CompositeNotifier` then has no delegates.
- **Email with missing config**: `NOTIFY_EMAIL_ENABLED=true` but blank `from` **or** empty `notification_recipients.email_to_csv` skips send and emits **WARN** `notification.failed` (`reason=not_configured`); checks still run and persist.
- **SMS with missing config**: `NOTIFY_SMS_ENABLED=true` but missing Twilio creds (`account-sid` / `auth-token` / `from`) **or** empty `notification_recipients.sms_to_csv` → same `reason=not_configured` pattern.
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

[application-test.yml](../src/test/resources/application-test.yml) replaces datasource with H2 (PostgreSQL mode), disables Flyway, sets JPA to `create-drop`, sets a dummy AlterLab key, disables the scheduler, excludes `MailSenderAutoConfiguration`, sets `app.notification.log.enabled`, `email.enabled`, and `sms.enabled` to `false` so tests do not emit SMTP/Twilio traffic or notifier noise, sets `app.ai.gemini.enabled=false` (with a blank `api-key`) to keep tests deterministic and offline — they exercise the deterministic fallback path instead, and mirrors `management.endpoints.web.exposure.include` with `show-details: never` for actuator.

Regression tests use Mockito’s **subclass** mock maker via [mockito-extensions/org.mockito.plugins.MockMaker](../src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker) so mocking concrete beans such as `CompositePriceFetcher` works on newer JDKs where the inline agent path is restricted.
