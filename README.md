# Amazon Price Monitor

Spring Boot 3 / Java 21 worker plus a small SPA: scheduled price checks for Amazon URLs, **Jsoup** first, **AlterLab** fallback, **PostgreSQL** + **Flyway**, configurable **log / Slack / noop** notifications on threshold breaches, **Chart.js** history UI.

The Git repository folder is named `AmazonPriceDropMontior` (as created on disk).

## Prerequisites

| Tool | Notes |
|------|--------|
| **JDK 21** | e.g. `brew install openjdk@21` then `export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` (Apple Silicon) or use [SDKMAN](https://sdkman.io/). This machine did not have a Java runtime when the project was scaffolded. |
| **Maven 3.9+** | Or generate the wrapper: `mvn -N wrapper:wrapper` from the project directory. |
| **Docker** | For local PostgreSQL via Compose. |

## Quick start

### Option A — Docker Compose (one command, app + Postgres)

1. Copy `.env.example` to `.env` and fill in any secrets (e.g. `ALTERLAB_API_KEY`, `SLACK_WEBHOOK_URL`). Inside Compose, `DB_HOST` is overridden to `postgres` automatically — your local `.env` value is ignored for the app container.

2. Build and start the stack:

   ```bash
   docker compose up --build
   ```

3. Open **http://localhost:8080/** for the UI, or use the JSON API under `/api/products`.

4. Tear down (keep data):

   ```bash
   docker compose down
   ```

   Tear down and wipe the DB volume:

   ```bash
   docker compose down -v
   ```

### Option B — Local Maven (development)

1. Start just the database:

   ```bash
   docker compose up -d postgres
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

5. Optional: **http://localhost:8080/actuator/health** (liveness + DB) and **/actuator/info**. Default console logs are **JSON** (non-`test` profile) via Logstash Logback encoder — use `jq` or set profile `test` for human-readable lines.

## Configuration

Environment variables map to `src/main/resources/application.yml`. See `.env.example` for a checklist.

- **`ALTERLAB_API_KEY`**: required for AlterLab fallback ([docs](https://alterlab.io/docs/api/rest)).
- **`NOTIFICATION_TYPE`**: `log` (default), `slack`, or `noop`. `log` prints a structured WARN on price alerts; `slack` posts to **`SLACK_WEBHOOK_URL`** via `app.notification.slack.webhook-url`; `noop` disables notifier output.
- **`SLACK_WEBHOOK_URL`**: used when `NOTIFICATION_TYPE=slack`; if unset, Slack delivery is skipped (checks still run).
- **`SCHEDULER_INTERVAL_MS`**: default delay in milliseconds when the `scheduler_settings` row is first created without Flyway (default 1 hour). The **live** interval is stored in the database and can be changed in the UI (**Checks every … min** → **Save interval**). First scheduled run still starts **60s** after boot; the **3s** post-add check in the SPA is unchanged.
- **`SCHEDULER_ENABLED=false`**: turns off the background scheduler (useful with tests or manual-only runs).
- **`GEMINI_API_KEY`** / **`GEMINI_ENABLED`**: enrich threshold-breach notifications with a 1–2 sentence Google Gemini summary of the trailing 7 days. With no key (or `GEMINI_ENABLED=false`), a deterministic fallback summary computed from the DB is shipped instead — the alert path is never blocked on AI. See [docs/monitoring-and-alerts.md](docs/monitoring-and-alerts.md#ai-assisted-change-summary) for the accuracy strategy and event catalog.

## API

| Method | Path | Purpose |
|--------|------|--------|
| GET | `/api/products` | List products |
| POST | `/api/products` | Create product (`amazonUrl`, required `displayName`, at least one of `thresholdPct` / `thresholdAmount`, `active`) |
| DELETE | `/api/products/{id}` | Remove product and history |
| GET | `/api/products/{id}/price-history` | Audit trail for charting |
| POST | `/api/admin/run-checks` | Run checks immediately (no auth in this prototype) |
| GET | `/api/admin/scheduler-settings` | Current scheduled check interval (`checkIntervalMs`) |
| PUT | `/api/admin/scheduler-settings` | Set interval (`{"checkIntervalMs": 3600000}` — min 60s, max 7 days) |

## Architecture notes

- **Drop detection** compares the new successful price to the **previous successful** check for the same product, then fires Slack if **either** configured threshold is met: percent drop **≥** `thresholdPct` (when set) **or** dollar drop **≥** `thresholdAmount` (when set).
- Failed checks are stored with `fetchMethod` **`FAILED`** when neither Jsoup nor AlterLab returns a price.

## License

Use and modify as needed for your project.
