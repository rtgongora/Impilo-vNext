-- scheduling-service V001 — tenant-scoped slot holds + outbox
CREATE SCHEMA IF NOT EXISTS scheduling;

CREATE TABLE scheduling.slot_reservations (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    resource_id     VARCHAR(255)    NOT NULL,
    slot_date       DATE            NOT NULL,
    start_time      TIME            NOT NULL,
    hold_token      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_scheduling_slot UNIQUE (tenant_id, resource_id, slot_date, start_time)
);

CREATE INDEX idx_scheduling_slot_tenant_resource ON scheduling.slot_reservations (tenant_id, resource_id, slot_date);

CREATE TABLE scheduling.capacity_rules (
    tenant_id           UUID            PRIMARY KEY,
    version             VARCHAR(32)     NOT NULL DEFAULT 'mvp-0.1',
    overbooking_factor  NUMERIC(6, 2)   NOT NULL DEFAULT 1.0,
    waitlist_enabled    BOOLEAN         NOT NULL DEFAULT false,
    holiday_calendar    VARCHAR(64)     NOT NULL DEFAULT 'NOT_CONFIGURED',
    notes               TEXT,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE scheduling.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_scheduling_outbox_unpublished
    ON scheduling.event_outbox (created_at)
    WHERE published_at IS NULL;
