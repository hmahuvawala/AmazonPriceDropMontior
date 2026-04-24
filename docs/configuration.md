# Configuration

All runtime config lives in [application.yml](../src/main/resources/application.yml) and is bound to typed `@ConfigurationProperties` classes registered in [AppConfiguration](../src/main/java/com/amazonpricemonitor/config/AppConfiguration.java).

`AppConfiguration` also exposes the singleton `RestClient.Builder` bean used by `SlackNotificationService` (AlterLab builds its own `RestClient` with custom timeouts).

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
| `slack.webhook-url` | `SLACK_WEBHOOK_URL` | *(empty)* | [SlackProperties](../src/main/java/com/amazonpricemonitor/config/SlackProperties.java); alerts skipped if empty |
| `app.scheduler.enabled` | `SCHEDULER_ENABLED` | `true` | gates [PriceCheckScheduler](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java) bean via `@ConditionalOnProperty` |
| `app.scheduler.interval-ms` | `SCHEDULER_INTERVAL_MS` | `3600000` (1h) | `@Scheduled(fixedDelayString)` |
| `app.jsoup.connect-timeout-ms` | — | `10000` | [JsoupClientProperties](../src/main/java/com/amazonpricemonitor/config/JsoupClientProperties.java) |
| `app.jsoup.read-timeout-ms` | — | `15000` | Jsoup `.timeout()` |
| `app.jsoup.user-agent` | — | macOS Chrome 120 UA string | Jsoup `.userAgent()` |

`.env.example` mirrors the above and is what the README points users at.

## Behavior gates

- **Scheduler off**: set `SCHEDULER_ENABLED=false` (or `app.scheduler.enabled: false`). The bean is conditionally created. Manual `POST /api/admin/run-checks` still works.
- **Slack off**: leave `SLACK_WEBHOOK_URL` empty. `SlackNotificationService.notifyPriceDrop` returns early after a debug log; checks still run and persist.
- **AlterLab off**: leave `ALTERLAB_API_KEY` empty. `AlterLabPriceClient.fetch` logs WARN and returns `Optional.empty()`; if Jsoup also fails, the row is `FAILED`.

## Subtleties

- `JsoupClientProperties.connect-timeout-ms` is **declared but not used** — the Jsoup fetcher only passes `read-timeout-ms` into `Jsoup.connect(...).timeout(...)`. Passing the same value also covers connect timeout in Jsoup's API.
- `AlterLabPriceClient` ignores `request-timeout-seconds` if it's below 30 (`Math.max(30, …)`).
- `app.scheduler.interval-ms` is read **twice**: once into `SchedulerProperties` (which nothing else reads) and once via `${...}` into `@Scheduled(fixedDelayString)`. Only the latter affects the schedule. Removing the bean field would not change behavior.
- Hibernate `time_zone` is forced to UTC; `TIMESTAMPTZ` columns; `open-in-view: false` (no lazy loading after the controller returns).
- Logging level for app code is `INFO` by default; fetchers log failures at DEBUG so failed Jsoup attempts are silent unless you bump the level.

## Test-profile overrides

[application-test.yml](../src/test/resources/application-test.yml) replaces datasource with H2 (PostgreSQL mode), disables Flyway, sets JPA to `create-drop`, sets a dummy AlterLab key, and disables the scheduler. Add new test-only overrides here.
