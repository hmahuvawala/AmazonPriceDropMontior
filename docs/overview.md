# Overview

Spring Boot 3.4 / Java 21 worker + small SPA. Periodically scrapes Amazon product pages for price, persists every check, and posts to Slack when a successful price drops by ≥ a per-product threshold.

## Stack

- **Java 21**, **Spring Boot 3.4.5** (parent POM), Maven build (no wrapper checked in).
- **PostgreSQL 16** in prod (Docker Compose); **H2** in PostgreSQL-mode for tests.
- **Flyway** for migrations (`src/main/resources/db/migration`). Hibernate is `ddl-auto: validate` in prod, `create-drop` + flyway disabled in tests.
- **Jsoup 1.18.3** for primary HTML scraping.
- **Spring `RestClient`** (built on JDK `HttpClient`) for AlterLab REST fallback and Slack webhook.
- **Vanilla JS + Chart.js 4.4 (CDN)** SPA served from `src/main/resources/static`.

## Top-level layout

```
AmazonPriceDropMontior/
├── pom.xml                     Maven; Spring Boot parent 3.4.5
├── docker-compose.yml          Local Postgres 16
├── .env.example                Env-var checklist
├── README.md                   User-facing quick start
├── context.md                  Short narrative of goals/decisions
└── src/
    ├── main/
    │   ├── java/com/amazonpricemonitor/
    │   │   ├── AmazonPriceMonitorApplication.java   @SpringBootApplication + @EnableScheduling
    │   │   ├── config/         @ConfigurationProperties beans + RestClient.Builder
    │   │   ├── domain/         JPA entities + FetchMethod enum
    │   │   ├── repository/     Spring Data JPA repos
    │   │   ├── service/        Fetchers, monitoring orchestrator, Slack, catalog, money parsing
    │   │   ├── scheduler/      @Scheduled trigger (gated by app.scheduler.enabled)
    │   │   └── web/            REST controllers + DTOs + GlobalExceptionHandler
    │   └── resources/
    │       ├── application.yml
    │       ├── db/migration/V1__init.sql
    │       └── static/         index.html, css/app.css, js/app.js
    └── test/
        ├── java/.../AmazonPriceMonitorApplicationTests.java   contextLoads only
        └── resources/application-test.yml                     H2 + flyway off + scheduler off
```

The git folder name `AmazonPriceDropMontior` is misspelled on disk — keep it; the README calls this out.

## Request/data flow

### Scheduled price check (default path)
1. [PriceCheckScheduler.runScheduledChecks](../src/main/java/com/amazonpricemonitor/scheduler/PriceCheckScheduler.java#L24) — fixed delay (default 1h), initial delay 60s. Bean is `@ConditionalOnProperty app.scheduler.enabled=true`.
2. [PriceMonitoringService.runChecksForActiveProducts](../src/main/java/com/amazonpricemonitor/service/PriceMonitoringService.java#L42) — loads `MonitoredProduct.active=true`, iterates, swallows per-product `RuntimeException` and writes a `FAILED` row.
3. For each product: read previous successful price → [CompositePriceFetcher.fetchWithFallback](../src/main/java/com/amazonpricemonitor/service/CompositePriceFetcher.java#L21) → Jsoup, then AlterLab.
4. On success: persist `PriceCheck(success=true, ...)`. Compute drop% vs prior successful. If `≥ thresholdPct`, [SlackNotificationService.notifyPriceDrop](../src/main/java/com/amazonpricemonitor/service/SlackNotificationService.java#L29).
5. On both-fetcher failure: persist `PriceCheck(success=false, fetchMethod=FAILED, errorMessage=...)`.

### Manual trigger
- `POST /api/admin/run-checks` → [AdminController.runChecksNow](../src/main/java/com/amazonpricemonitor/web/AdminController.java#L22) → same `runChecksForActiveProducts`. Returns 202 immediately but the call is synchronous on the request thread (no async dispatch).

### Catalog CRUD + history
- `GET/POST/DELETE /api/products`, `GET /api/products/{id}/price-history` → [ProductController](../src/main/java/com/amazonpricemonitor/web/ProductController.java) → [ProductCatalogService](../src/main/java/com/amazonpricemonitor/service/ProductCatalogService.java) → JPA repos.

### SPA
- `GET /` → static `index.html` → `js/app.js` calls the JSON API and renders Chart.js.

## Cross-cutting conventions

- All timestamps stored in UTC (`hibernate.jdbc.time_zone=UTC`, `TIMESTAMPTZ` columns).
- `@PrePersist` / `@PreUpdate` on `MonitoredProduct`, `@PrePersist` on `PriceCheck` set `created_at`/`updated_at`.
- Money is `BigDecimal`. Currency is hard-coded `"USD"` in the Jsoup path, defaulted to `"USD"` in the AlterLab path. There is no multi-currency support.
- Errors are logged at WARN/DEBUG inside fetchers and never thrown out of `fetch(...)`; they return `Optional.empty()`. The monitoring loop is what records `FAILED` rows.
- `GlobalExceptionHandler` maps `EntityNotFoundException` → 404 and validation errors → 400. Everything else surfaces as a default 500.
- No auth anywhere — README explicitly calls this a prototype.

## Sibling docs

- [build-and-run.md](build-and-run.md) — JDK/Maven/Docker setup, env vars, test command.
- [configuration.md](configuration.md) — every property + env-var mapping.
- [domain-and-db.md](domain-and-db.md) — entities, schema, migrations.
- [api.md](api.md) — REST endpoints, DTOs, validation, error shape.
- [price-fetching.md](price-fetching.md) — Jsoup, AlterLab, Composite, MoneyParsing.
- [monitoring-and-alerts.md](monitoring-and-alerts.md) — PriceMonitoringService, Scheduler, Slack.
- [frontend.md](frontend.md) — static SPA structure.
- [extension-points.md](extension-points.md) — where to plug in changes.
