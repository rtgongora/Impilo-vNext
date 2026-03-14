-- ============================================================================
-- surveillance-service V002 — Daily counters and alert thresholds
-- ============================================================================

-- Daily aggregate counters by facility and syndrome code
CREATE TABLE surv.daily_counter (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    facility_id         UUID            NOT NULL,
    syndrome_code       VARCHAR(128)    NOT NULL,
    count_date          DATE            NOT NULL,
    event_count         INT             NOT NULL DEFAULT 0,
    last_updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_surv_daily_counter UNIQUE (tenant_id, facility_id, syndrome_code, count_date)
);

CREATE INDEX idx_surv_counter_tenant ON surv.daily_counter (tenant_id);
CREATE INDEX idx_surv_counter_date ON surv.daily_counter (count_date);
CREATE INDEX idx_surv_counter_facility ON surv.daily_counter (facility_id);

-- Alert definitions — threshold-based alerts on counters
CREATE TABLE surv.alert_definition (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    syndrome_code       VARCHAR(128)    NOT NULL,
    threshold           INT             NOT NULL DEFAULT 5,
    window_days         INT             NOT NULL DEFAULT 1,
    severity            VARCHAR(32)     NOT NULL DEFAULT 'MEDIUM',
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_surv_alert_def_tenant ON surv.alert_definition (tenant_id);

-- Triggered alerts log
CREATE TABLE surv.alert_event (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    alert_definition_id BIGINT          NOT NULL REFERENCES surv.alert_definition(id),
    facility_id         UUID            NOT NULL,
    syndrome_code       VARCHAR(128)    NOT NULL,
    trigger_count       INT             NOT NULL,
    threshold           INT             NOT NULL,
    triggered_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    acknowledged        BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_surv_alert_event_tenant ON surv.alert_event (tenant_id);
CREATE INDEX idx_surv_alert_event_triggered ON surv.alert_event (triggered_at);
