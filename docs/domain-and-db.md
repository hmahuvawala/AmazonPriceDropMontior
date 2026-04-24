# Domain & database

## Schema (Flyway)

Single migration: [V1__init.sql](../src/main/resources/db/migration/V1__init.sql). Hibernate runs in `validate` mode against this — entities and SQL must stay in lockstep.

```sql
monitored_product (
  id            BIGSERIAL PK,
  amazon_url    VARCHAR(2048) NOT NULL,
  display_name  VARCHAR(512),
  threshold_pct NUMERIC(5,2) NOT NULL,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

price_check (
  id            BIGSERIAL PK,
  product_id    BIGINT NOT NULL REFERENCES monitored_product(id) ON DELETE CASCADE,
  success       BOOLEAN NOT NULL,
  price_amount  NUMERIC(12,2),         -- nullable on failure
  currency      VARCHAR(8),            -- nullable on failure
  fetch_method  VARCHAR(32) NOT NULL,  -- enum string: JSOUP | ALTERLAB | FAILED
  error_message VARCHAR(2000),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
)

INDEX idx_price_check_product_created (product_id, created_at DESC)
INDEX idx_monitored_product_active (active) WHERE active = TRUE
```

Adding columns/tables/indexes ⇒ create `V2__*.sql`, never edit `V1`.

## Entities

### [MonitoredProduct](../src/main/java/com/amazonpricemonitor/domain/MonitoredProduct.java)
- `@Entity @Table(name = "monitored_product")`, `IDENTITY` id.
- `@PrePersist` sets both `createdAt` and `updatedAt` to `Instant.now()`. `@PreUpdate` bumps `updatedAt`.
- All timestamps are `Instant` (UTC).
- No setters for `id`/timestamps — only the JPA lifecycle writes them.

### [PriceCheck](../src/main/java/com/amazonpricemonitor/domain/PriceCheck.java)
- `@ManyToOne(fetch = LAZY, optional = false)` to `MonitoredProduct`.
- `fetchMethod` is `@Enumerated(EnumType.STRING)` — schema column is `VARCHAR(32)`. Renaming an enum constant is a breaking change to existing rows.
- `@PrePersist` only assigns `createdAt` if it's null (so callers can override for backfills/tests).
- Has a setter for `createdAt` (the only one of the timestamps that's mutable).

### [FetchMethod](../src/main/java/com/amazonpricemonitor/domain/FetchMethod.java)
Three values: `JSOUP`, `ALTERLAB`, `FAILED`. Used by `PriceQuote`, persisted as the price_check provenance, and rendered by both Slack messages and the SPA chart (color cue).

## Repositories

- [MonitoredProductRepository](../src/main/java/com/amazonpricemonitor/repository/MonitoredProductRepository.java)
  - `findByActiveTrueOrderByIdAsc()` — drives the scheduler iteration.
- [PriceCheckRepository](../src/main/java/com/amazonpricemonitor/repository/PriceCheckRepository.java)
  - `findByProductIdOrderByCreatedAtAsc(Long, Pageable)` — history endpoint, capped at 250 in service layer.
  - `findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(Long)` — "previous successful price" lookup for drop detection.

## Invariants worth preserving

- A `PriceCheck` row is written for **every** scheduler iteration of an active product — successes (`success=true`, price+currency populated) and failures (`success=false`, `fetch_method=FAILED`, `error_message` set).
- Drop detection compares against the most recent `success=true` row, ignoring all `FAILED` rows in between (so an outage window doesn't suppress alerts when the next success comes in).
- Currency on success rows is `"USD"` for Jsoup; for AlterLab it's whatever JSON's `content.json.currency` returns, defaulting to `"USD"`. There is no normalization across currencies before computing drop %.
- `ON DELETE CASCADE` means deleting a product purges its price history. The catalog service relies on this — it does not delete `price_check` rows manually.
