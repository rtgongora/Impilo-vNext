-- =============================================
-- Service : oros-service
-- Schema  : oros
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS oros;

CREATE TABLE oros.clinical_order (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    order_ref           UUID            NOT NULL UNIQUE,
    encounter_id        UUID            NOT NULL,
    subject_cpid        VARCHAR(64)     NOT NULL,
    facility_id         UUID            NOT NULL,
    order_type          VARCHAR(50)     NOT NULL,
    order_code          VARCHAR(100)    NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PLACED',
    placed_by           UUID            NOT NULL,
    placed_at           TIMESTAMPTZ     DEFAULT now(),
    accepted_at         TIMESTAMPTZ,
    resulted_at         TIMESTAMPTZ,
    acked_at            TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    routing_target      VARCHAR(100),
    correlation_keys    JSONB,
    created_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_clinical_order_tenant_status_facility ON oros.clinical_order (tenant_id, status, facility_id);
CREATE INDEX idx_clinical_order_tenant_encounter ON oros.clinical_order (tenant_id, encounter_id);
