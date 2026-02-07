-- =============================================
-- Service : pharmacy-service
-- Schema  : pharmacy
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS pharmacy;

CREATE TABLE pharmacy.dispense (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dispense_ref        UUID            NOT NULL UNIQUE,
    order_id            UUID            NOT NULL,
    subject_cpid        VARCHAR(64)     NOT NULL,
    medication_code     VARCHAR(100)    NOT NULL,
    quantity_dispensed   NUMERIC(10,2),
    status              VARCHAR(30)     DEFAULT 'PENDING',
    dispensed_by        UUID,
    dispensed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_dispense_tenant_order ON pharmacy.dispense (tenant_id, order_id);
