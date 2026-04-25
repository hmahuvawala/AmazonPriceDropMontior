## Current Goals

- Operate the Amazon Price Monitor locally: PostgreSQL via Docker, Spring Boot app, AlterLab + Slack env vars configured.

## Architectural Decisions

- **Java 21 + Spring Boot 3.4** with **JPA** + **Flyway** on **PostgreSQL**.
- **Jsoup** first for price extraction; **AlterLab** `POST /scrape` with `formats: ["json"]` as fallback ([REST API](https://alterlab.io/docs/api/rest)).
- **Notifications**: `NOTIFICATION_TYPE` selects `log` (default WARN), `slack` (incoming webhook), or `noop`. Outcomes persisted in **`price_check`** for auditing.
- **Vanilla JS + Chart.js** (CDN) served from **`src/main/resources/static`**.

## Recent Changes

- Initial scaffold: domain model, migrations, scheduler, REST API, AlterLab/Jsoup/Slack services, SPA, Docker Compose, tests (H2, scheduler off).
- **Plan 1:** Optional `threshold_amount` + nullable `threshold_pct` with DB `CHECK`; OR semantics for Slack alerts; API/SPA/docs updated.
- **Plan 2:** `Notifier` abstraction (`log` default, `slack`, `noop`); `NotificationProperties` + `app.notification.*`; removed `SlackNotificationService` / `SlackProperties`.
- **Plan 3:** Structured JSON logging via `logstash-logback-encoder` (non-`test` profile); MDC (`runId`, `productId`, `amazonUrl`) and stable `event=` fields across `PriceMonitoringService` / notifiers; Spring Boot Actuator `/actuator/health` + `/actuator/info` exposed.
- **Plan 4:** Layered tests added — `MoneyParsingTest` (pure JUnit), `JsoupAmazonPriceFetcherTest` (HTML fixtures under `src/test/resources/fixtures/amazon`), `PriceMonitoringServiceIntegrationTest` (`@SpringBootTest` + mocked fetcher/notifier), `PriceMonitoringServiceTest` (Mockito-only), `SlackNotifierTest` (`MockRestServiceServer`). Mockito subclass mock maker pinned for Java 25 compatibility.
- **Containerization & CI:** Multi-stage `Dockerfile` (`maven:3.9-eclipse-temurin-21` builder → `eclipse-temurin:21-jre` runtime, non-root `app` user, `HEALTHCHECK` against `/actuator/health`); `.dockerignore`; `docker-compose.yml` extended with an `app` service that `depends_on` `postgres` healthy, overrides `DB_HOST=postgres`, and pass-through env vars with `${VAR:-default}`; GitHub Actions workflow `.github/workflows/ci.yml` runs `mvn -B -ntp test` on `push` / `pull_request` (Temurin 21, Maven cache, surefire reports uploaded on failure); README quick start now documents `docker compose up --build` plus a Maven dev path; `.env.example` notes the `DB_HOST=postgres` override inside Compose.
- **AI-assisted change summary (Plan 5):** New `service/ai/` package — `GeminiProperties` (`@ConfigurationProperties("app.ai.gemini")`), `PriceTrendStats` record, `GeminiClient` (RestClient + `SimpleClientHttpRequestFactory` hard timeout, registered via `AppConfiguration#geminiRestClient` to leave the shared `RestClient.Builder` unmutated), `PriceChangeSummaryService` (deterministic 7-day stats from `price_check` → strict system prompt to Gemini → sanitized / capped output, deterministic fallback on disable / failure / blank). `Notifier.notifyPriceDrop` extended with an `aiSummary` parameter (Slack appends a `_7-day summary:_ …` line, escaped; `LogNotifier` adds an `aiSummary` MDC key). `PriceCheckRepository` got `findByProductIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc`. `application.yml` adds `app.ai.gemini.*` (env-driven), `application-test.yml` disables Gemini, `.env.example` and `docker-compose.yml` pass `GEMINI_*` through. New tests: `GeminiClientTest` (`MockRestServiceServer` happy path + 4xx/5xx/empty/safety), `PriceChangeSummaryServiceTest` (Mockito on repo + client; fallback paths, sanitization, stats math). Existing tests updated for the new `Notifier` signature.
