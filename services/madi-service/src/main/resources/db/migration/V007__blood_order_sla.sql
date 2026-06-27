-- =============================================
-- Service : madi-service
-- Flyway  : V007__blood_order_sla.sql
-- Purpose : Blood-order SLA timers — enforce turnaround targets per stage (e.g. crossmatch within
--           N minutes of submit, issue within N minutes of crossmatch). A scheduled breach detector
--           marks overdue open timers and emits an SLA_BREACHED event for the dashboard / escalation.
-- =============================================

CREATE TABLE blood_order_sla (
    sla_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID            NOT NULL,
    order_id      UUID            NOT NULL,
    stage         VARCHAR(32)     NOT NULL,            -- CROSSMATCH, ISSUE
    target_at     TIMESTAMPTZ     NOT NULL,
    status        VARCHAR(16)     NOT NULL DEFAULT 'OPEN',  -- OPEN, MET, BREACHED
    started_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    breached_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_blood_order_sla_order ON blood_order_sla (order_id);
-- Drives the breach scan: open timers past their target.
CREATE INDEX idx_blood_order_sla_open ON blood_order_sla (status, target_at) WHERE status = 'OPEN';
