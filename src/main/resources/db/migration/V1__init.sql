CREATE TABLE monitored_product (
    id              BIGSERIAL PRIMARY KEY,
    amazon_url      VARCHAR(2048) NOT NULL,
    display_name    VARCHAR(512),
    threshold_pct   NUMERIC(5, 2) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE price_check (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES monitored_product (id) ON DELETE CASCADE,
    success         BOOLEAN NOT NULL,
    price_amount    NUMERIC(12, 2),
    currency        VARCHAR(8),
    fetch_method    VARCHAR(32) NOT NULL,
    error_message   VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_check_product_created ON price_check (product_id, created_at DESC);
CREATE INDEX idx_monitored_product_active ON monitored_product (active) WHERE active = TRUE;
