# Build & run

## Prereqs

- **JDK 21** (project requires it via `<java.version>21`). The original scaffold machine had no JRE — `brew install openjdk@21` then export `PATH` (Apple Silicon) or use SDKMAN.
- **Maven 3.9+**. No Maven wrapper is committed; generate one with `mvn -N wrapper:wrapper` if desired.
- **Docker** for local Postgres via `docker-compose.yml`.

## Local run

```bash
docker compose up -d                # starts postgres on 5432, db=amazon_price_monitor user=monitor pw=monitor
export ALTERLAB_API_KEY="..."       # required for AlterLab fallback
export SLACK_WEBHOOK_URL="..."      # optional; alerts skipped if unset
mvn spring-boot:run                 # serves SPA at http://localhost:8080/
```

## Tests

```bash
mvn test
```

The single test [AmazonPriceMonitorApplicationTests](../src/test/java/com/amazonpricemonitor/AmazonPriceMonitorApplicationTests.java) is a `@SpringBootTest` `contextLoads()`. It uses the `test` profile from [application-test.yml](../src/test/resources/application-test.yml):

- H2 in-memory DB in `MODE=PostgreSQL` (Flyway disabled, JPA `create-drop`).
- `app.scheduler.enabled=false` so no background work fires during the context load.
- `alterlab.api-key=test-key` is set just so `AlterLabProperties.isConfigured()` is true; AlterLab is not actually called.

There are **no** unit tests of services, controllers, fetchers, or the monitoring loop. New tests are an obvious gap.

## Build artifacts

Standard Spring Boot Maven plugin. Run `mvn package` to produce a fat jar in `target/`. There is no Dockerfile for the app itself; only the Postgres compose file.

## Conventions for code changes

- Hibernate is `ddl-auto: validate` against Flyway-managed schema → **any new column, table, or index requires a new `V{N}__*.sql` migration**, never an entity-only change.
- `application.yml` defaults are duplicated by Java defaults inside the `@ConfigurationProperties` classes (e.g. `JsoupClientProperties` hard-codes the same UA). When changing a default in one place, update the other or remove the duplicate.
- Tests run with `application-test.yml` overlaying `application.yml`. Flyway is off there, so any schema you rely on must be created by JPA's `create-drop` from entity annotations.
