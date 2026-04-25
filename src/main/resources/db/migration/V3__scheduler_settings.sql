CREATE TABLE scheduler_settings (
    id                  SMALLINT PRIMARY KEY,
    check_interval_ms   BIGINT NOT NULL,
    CONSTRAINT chk_scheduler_interval_ms CHECK (
        check_interval_ms >= 60000
        AND check_interval_ms <= 604800000
    )
);

INSERT INTO scheduler_settings (id, check_interval_ms)
VALUES (1, 3600000);
