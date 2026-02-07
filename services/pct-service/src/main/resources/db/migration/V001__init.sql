-- =============================================
-- Service : pct-service
-- Schema  : pct
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS pct;

CREATE TABLE pct.encounter (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    encounter_ref       UUID            NOT NULL UNIQUE,
    subject_cpid        VARCHAR(64)     NOT NULL,
    facility_id         UUID            NOT NULL,
    workspace_id        UUID,
    encounter_type      VARCHAR(50)     NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'ARRIVED',
    primary_provider_id UUID,
    started_at          TIMESTAMPTZ     DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_encounter_tenant_subject ON pct.encounter (tenant_id, subject_cpid);
CREATE INDEX idx_encounter_tenant_status_facility ON pct.encounter (tenant_id, status, facility_id);
