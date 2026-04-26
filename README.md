# Amazon Price Monitor

Spring Boot 3 / Java 21 worker plus a small SPA: scheduled price checks for Amazon URLs, **Jsoup** first, **AlterLab** fallback, **PostgreSQL** + **Flyway**, configurable **log / email / SMS** notifications on threshold breaches, **Chart.js** history UI.

The Git repository folder is named `AmazonPriceDropMontior` (as created on disk).

## Prerequisites

| Tool | Notes |
|------|--------|
| **JDK 21** | e.g. `brew install openjdk@21` then `export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"` (Apple Silicon) or use [SDKMAN](https://sdkman.io/). This machine did not have a Java runtime when the project was scaffolded. |
| **Maven 3.9+** | Or generate the wrapper: `mvn -N wrapper:wrapper` from the project directory. |
| **Docker** | For local PostgreSQL via Compose. |

## Quick start

### Option A — Docker Compose (one command, app + Postgres)

1. Copy `.env.example` to **`.env.local`** in the **`AmazonPriceDropMontior/`** directory (same folder as `docker-compose.yml`) and fill in secrets. Compose loads that file into the **`app`** container via `env_file`; `DB_HOST` is still forced to **`postgres`** there so the app reaches the Compose Postgres service. Without `.env.local`, `docker compose up` will fail.

2. Build and start the stack (pass the same file so `${DB_USER}` / `${SERVER_PORT}` in `docker-compose.yml` interpolate for **postgres** and port mapping, not only for the `app` container):

   ```bash
   docker compose --env-file .env.local up --build
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
   # Optional: export MAIL_* and NOTIFY_EMAIL_* for SMTP alerts, or Twilio vars for SMS.
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
- **`NOTIFY_LOG_ENABLED`**: default `true` — structured **INFO** `notification.sent` lines on threshold breaches.
- **`NOTIFY_EMAIL_ENABLED`** + **`MAIL_*`** + **`NOTIFY_EMAIL_FROM`**: SMTP via Spring Mail when enabled and configured.
- **`NOTIFY_SMS_ENABLED`** + **`TWILIO_*`** (incl. `TWILIO_FROM`): Twilio SMS when enabled and configured. SMS bodies are capped at ~320 characters (AI summary truncated if needed).
- **Recipients (email addresses + SMS phone numbers)** are stored in the DB (`notification_recipients` row, seeded empty by Flyway V4) and edited via the SPA or `PUT /api/admin/notification-recipients`. They are validated on save (RFC-style email regex, **E.164** phones). An empty list short-circuits that channel with `notification.failed` (`reason=not_configured`) even when the channel is otherwise enabled.
- Turn **all** channels off with `NOTIFY_LOG_ENABLED=false` and email/SMS disabled — no notifier output (checks still run).
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
| POST | `/api/admin/send-test-notification` | **Optional:** when `ADMIN_ALLOW_TEST_NOTIFICATION=true`, sends one **synthetic** price-drop notification through the real notifier (email/SMS/log) without changing the database — use to verify SMTP/Twilio. Returns **404** when disabled. |
| GET | `/api/admin/scheduler-settings` | Current scheduled check interval (`checkIntervalMs`) |
| PUT | `/api/admin/scheduler-settings` | Set interval (`{"checkIntervalMs": 3600000}` — min 60s, max 7 days) |
| GET | `/api/admin/notification-recipients` | Current email + SMS recipient CSVs (`{"emailToCsv": "...", "smsToCsv": "..."}`) |
| PUT | `/api/admin/notification-recipients` | Replace recipients. Same shape; emails validated by regex, phones must be **E.164**. 400 on invalid input. |

### Verify email / SMS without a real price drop

1. **Add at least one recipient** via `PUT /api/admin/notification-recipients` (or the SPA) — without one, the channel short-circuits with `reason=not_configured` regardless of credentials. Example:
   ```bash
   curl -X PUT http://localhost:8080/api/admin/notification-recipients \
        -H 'Content-Type: application/json' \
        -d '{"emailToCsv":"you@example.com","smsToCsv":"+15551231234"}'
   ```
2. Set **`ADMIN_ALLOW_TEST_NOTIFICATION=true`** in `.env.local` (or export it), restart the app, and ensure email/SMS env vars are set as usual (creds + `*_FROM`).
3. Call **`POST http://localhost:8080/api/admin/send-test-notification`** (e.g. `curl -X POST http://localhost:8080/api/admin/send-test-notification`).
4. **Success signals:** JSON `{"status":"dispatched",...}` and **202**; logs show **`notification.sent`** (`channel=email` / `sms` / `log`) or **`notification.failed`** with a reason; check the recipient inbox/phone.

## Architecture notes

- **Drop detection** compares the new successful price to the **previous successful** check for the same product, then fires configured notifier channels if **either** threshold is met: percent drop **≥** `thresholdPct` (when set) **or** dollar drop **≥** `thresholdAmount` (when set).
- Failed checks are stored with `fetchMethod` **`FAILED`** when neither Jsoup nor AlterLab returns a price.
- See [`docs/DESIGN-DOC.md`](docs/DESIGN-DOC.md) for the project design document covering motivation, approach, tech stack, tradeoffs, and future enhancements.
- See [`docs/AI-NOTES.md`](docs/AI-NOTES.md) for a short reflection on what the AI assistant got wrong or oversimplified during the build and how those issues were caught and fixed.

## License

Use and modify as needed for your project.
