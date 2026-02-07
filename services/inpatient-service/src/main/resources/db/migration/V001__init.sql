-- =============================================
-- Service : inpatient-service
-- Schema  : inpatient
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS inpatient;

CREATE TABLE inpatient.admission (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    admission_ref   UUID            NOT NULL UNIQUE,
    encounter_id    UUID            NOT NULL,
    subject_cpid    VARCHAR(64)     NOT NULL,
    facility_id     UUID            NOT NULL,
    ward_id         UUID,
    bed_id          UUID,
    status          VARCHAR(30)     DEFAULT 'ADMITTED',
    admitted_at     TIMESTAMPTZ     DEFAULT now(),
    discharged_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_admission_tenant_facility_status ON inpatient.admission (tenant_id, facility_id, status);
