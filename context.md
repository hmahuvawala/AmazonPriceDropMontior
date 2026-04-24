## Current Goals

- Operate the Amazon Price Monitor locally: PostgreSQL via Docker, Spring Boot app, AlterLab + Slack env vars configured.

## Architectural Decisions

- **Java 21 + Spring Boot 3.4** with **JPA** + **Flyway** on **PostgreSQL**.
- **Jsoup** first for price extraction; **AlterLab** `POST /scrape` with `formats: ["json"]` as fallback ([REST API](https://alterlab.io/docs/api/rest)).
- **Slack** incoming webhook for threshold alerts; outcomes persisted in **`price_check`** for auditing.
- **Vanilla JS + Chart.js** (CDN) served from **`src/main/resources/static`**.

## Recent Changes

- Initial scaffold: domain model, migrations, scheduler, REST API, AlterLab/Jsoup/Slack services, SPA, Docker Compose, tests (H2, scheduler off).
