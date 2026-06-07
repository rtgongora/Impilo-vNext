-- MADI remaining gaps: emergency redistribution workflow
-- Service : madi-service
-- Flyway  : V003__remaining_gaps.sql

CREATE TABLE madi.blood_emergency_redistributions (
    id                  BIGSERIAL PRIMARY KEY,
    redistribution_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    source_facility_id  UUID NOT NULL,
    target_facility_id  UUID NOT NULL,
    blood_group         VARCHAR(8) NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    units_requested     INT NOT NULL,
    units_allocated     INT NOT NULL DEFAULT 0,
    priority            VARCHAR(16) NOT NULL DEFAULT 'EMERGENCY',
    status              VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    reason              TEXT,
    requested_by        VARCHAR(128),
    approved_by         VARCHAR(128),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at         TIMESTAMPTZ,
    fulfilled_at        TIMESTAMPTZ,
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_madi_emergency_redist_tenant
    ON madi.blood_emergency_redistributions (tenant_id, status, requested_at DESC);
