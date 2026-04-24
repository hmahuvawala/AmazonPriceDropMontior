# Amazon Price Monitor

Spring Boot 3 / Java 21 worker plus a small SPA: scheduled price checks for Amazon URLs, **Jsoup** first, **AlterLab** fallback, **PostgreSQL** + **Flyway**, **Slack** webhook on threshold breaches, **Chart.js** history UI.

The Git repository folder is named `AmazonPriceDropMontior` (as created on disk).

## Prerequisites

| Tool | Notes |
|------|--------|
| **JDK 21** | e.g. `brew install openjdk@21` then `export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` (Apple Silicon) or use [SDKMAN](https://sdkman.io/). This machine did not have a Java runtime when the project was scaffolded. |
| **Maven 3.9+** | Or generate the wrapper: `mvn -N wrapper:wrapper` from the project directory. |
| **Docker** | For local PostgreSQL via Compose. |

## Quick start

1. Start the database:

   ```bash
   docker compose up -d
   ```

2. Set secrets (do not commit real keys):

   ```bash
   export ALTERLAB_API_KEY="your-key"
   export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/..."
   ```

3. Run the app:

   ```bash
   mvn spring-boot:run
   ```

4. Open **http://localhost:8080/** for the UI, or use the JSON API under `/api/products`.

## Configuration

Environment variables map to `src/main/resources/application.yml`. See `.env.example` for a checklist.

- **`ALTERLAB_API_KEY`**: required for AlterLab fallback ([docs](https://alterlab.io/docs/api/rest)).
- **`SLACK_WEBHOOK_URL`**: optional; alerts are skipped if unset.
- **`SCHEDULER_INTERVAL_MS`**: delay between scheduler runs (default 1 hour). First run starts **60s** after boot.
- **`SCHEDULER_ENABLED=false`**: turns off the background scheduler (useful with tests or manual-only runs).

## API

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/api/products` | List products |
| POST | `/api/products` | Create product (`amazonUrl`, `thresholdPct`, optional `displayName`, `active`) |
| DELETE | `/api/products/{id}` | Remove product and history |
| GET | `/api/products/{id}/price-history` | Audit trail for charting |
| POST | `/api/admin/run-checks` | Run checks immediately (no auth in this prototype) |

## Architecture notes

- **Drop detection** compares the new successful price to the **previous successful** check for the same product, then fires Slack if the drop percentage is **≥** `thresholdPct`.
- Failed checks are stored with `fetchMethod` **`FAILED`** when neither Jsoup nor AlterLab returns a price.

## License

Use and modify as needed for your project.
