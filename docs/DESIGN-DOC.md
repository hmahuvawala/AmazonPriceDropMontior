# Design Document - Price Drop Monitor

## 1. Why? / Motivation

The objective is to build a reliable, automated service that tracks a configurable list of Amazon products and alerts users when prices drop below a specific threshold.

From an engineering standpoint, the goal is to design a system that is simple enough to be built and tested within a tight 2-to-4 hour window, yet robust enough to handle real-world scraping failures such as anti-bot measures and DOM changes. The architecture prioritizes clean separation of concerns, durable telemetry for easy debugging, and a resilient, cost-aware approach to data fetching.

## 2. Solution / Approach

The solution is a standalone, scheduled background worker with a fallback scraping mechanism.

A scheduled job wakes up at a defined interval and iterates through active products stored in the database. To ensure reliable delivery within the time constraint, the baseline implementation uses the AlterLab API to fetch product prices.

The optimization, time permitting, is to attempt direct scraping with Jsoup first to minimize API costs. If Jsoup fails due to missing CSS selectors or Amazon blocking the request, the system gracefully catches the error and falls back to the AlterLab API to ensure the price check still succeeds.

The system compares the fetched price against the durable price history table. If a price drop exceeds the configured percentage threshold or absolute-value threshold, it fires a notification via email or SMS.

The outcome, including success or failure, price, and the fetch method used (`JSOUP` or `ALTERLAB`), is saved to the database to ensure an unbroken, auditable history.

A Vanilla JavaScript single page application (SPA) is served directly from the backend, providing a UI to add and remove products and view a Chart.js visualization of price history.

## 3. Exact Tech Stack

- **Language & Framework:** Java 21 with Spring Boot 3, chosen for robust dependency injection and built-in scheduling.
- **Primary/Fallback Scraper:** AlterLab API via Spring `RestClient` to guarantee reliable data extraction.
- **Optimization Scraper:** Jsoup for native HTML parsing to reduce API dependency.
- **Database:** PostgreSQL + Flyway.
- **Data Access & Migrations:** Spring Data JPA with Flyway for explicit schema management.
- **Notifications:** JavaMail for email, Twilio for SMS, and Google Gemini for generating optional AI-enriched notification content.
- **Frontend:** Vanilla JavaScript, HTML, and Chart.js served from Spring Boot static resources.
- **Logging:** Logback + LogstashEncoder for structured JSON logs.

## 4. Product Details / Tradeoffs

### Tradeoff A: Scraping Strategy - Jsoup Primary, AlterLab Fallback

**Detail:** The system first uses Jsoup against the user-defined list of Amazon products. If that does not work, it falls back to AlterLab, a third-party API with cheap pricing and an easy-to-parse response format: about $1 for 5,000 requests.

**Tradeoff:** AlterLab-only would be cheap to write but more expensive to run and would scale poorly per request. Jsoup-only would be free but brittle against Amazon's bot mitigation. A fixed IP can quickly start receiving 503 or CAPTCHA responses.

### Tradeoff B: Storage - PostgreSQL via Docker vs. SQLite

**Detail:** SQLite would have shaved roughly 10 minutes off the Docker Compose setup.

**Tradeoff:** PostgreSQL immediately supports the "10x scale" requirement. It handles concurrent writes natively if the system scales out to multiple worker threads, and it supports JSONB columns, which allows raw scraper responses to be stored durably and efficiently. The Docker Compose setup absorbs infrastructure complexity upfront and satisfies the deployability stretch goal.

### Tradeoff C: Frontend Architecture - Server-Served Static SPA vs. Decoupled React App

**Detail:** The dashboard is built with Vanilla JavaScript and Chart.js, served as static files directly from the Spring Boot embedded Tomcat server.

**Tradeoff:** A decoupled frontend like React or Vue offers better component reusability and a stronger developer experience for large teams. However, for a microservice with a tight time constraint, it introduces unnecessary complexity such as CORS configuration, separate build pipelines, and multi-container deployments. By serving a lightweight Vanilla JS SPA from Spring Boot, the project keeps a seamless single-click deployment for the reviewer while still decoupling the API layer from the UI presentation layer.

### Tradeoff D: Scheduling - Spring `@Scheduled` vs. OS Cron

**Detail:** The checking loop uses Spring Boot's native `@Scheduled` annotation.

**Tradeoff:** An external scheduler like OS cron completely decouples scheduling from execution, meaning if the app crashes, the schedule is unaffected. However, it significantly increases deployment complexity. An in-process scheduler keeps the application self-contained and testable. The risk of the process dying is deferred to the infrastructure layer, such as relying on a Docker `restart: always` policy.

## 5. Questions Left to Answer / Future Enhancements

- **Authentication:** This is a highly user-defined application, so authentication is an absolute necessity. Authentication should be the first future enhancement.
- **Terms of Service Compliance:** For this prototype, direct DOM parsing via Jsoup is used to demonstrate multi-layered scraping. Production usage at scale would likely violate Amazon's automated access policies. A true production rollout would either rely entirely on an approved API or service, or require robust proxy rotation and adherence to `robots.txt`.
- **Cost Monitoring & Rate Limiting:** If the Jsoup path fails frequently, the system will hit AlterLab API limits quickly. A future version should implement a circuit breaker such as Resilience4j and track API usage per cycle to avoid accidental cost spikes if Amazon fully blocks the application's IPs.
- **Notification Idempotency:** If the application successfully sends a notification but the database connection drops before it records that the notification was sent, the system may send a duplicate alert on the next run. Future iterations should use a more transactional outbox pattern.
