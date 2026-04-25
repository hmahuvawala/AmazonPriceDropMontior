ALTER TABLE monitored_product
    ADD COLUMN threshold_amount NUMERIC(12, 2) NULL;

ALTER TABLE monitored_product
    ALTER COLUMN threshold_pct DROP NOT NULL;

ALTER TABLE monitored_product
    ADD CONSTRAINT chk_threshold_present CHECK (
        threshold_pct IS NOT NULL OR threshold_amount IS NOT NULL);
