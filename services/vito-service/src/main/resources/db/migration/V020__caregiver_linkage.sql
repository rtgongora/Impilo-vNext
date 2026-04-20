-- =============================================================================
-- VITO V020 — Caregiver Linkage Model (Health OS §4, §5)
--
-- "One human being may lawfully hold multiple other identifiers linked to role,
--  organization, function, domain object, or transaction."
--
-- A caregiver linkage connects one person (the caregiver) to one or more
-- persons they provide care for (the subject). This enables:
-- - Family caregivers viewing a dependant's records
-- - Delegated care tasks and notifications
-- - Caregiver-specific consent directives
-- - Care partner coordination
-- =============================================================================

CREATE TABLE vito.caregiver_linkages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255)    NOT NULL,
    caregiver_health_id VARCHAR(255)    NOT NULL,
    subject_health_id   VARCHAR(255)    NOT NULL,
    relationship        VARCHAR(128)    NOT NULL,
    linkage_type        VARCHAR(64)     NOT NULL DEFAULT 'FAMILY',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    granted_scopes      JSONB           NOT NULL DEFAULT '["VIEW_SUMMARY"]'::jsonb,
    consent_ref         VARCHAR(255),
    granted_by          VARCHAR(255)    NOT NULL,
    granted_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revoked_by          VARCHAR(255),
    revocation_reason   TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_caregiver_subject UNIQUE (tenant_id, caregiver_health_id, subject_health_id, linkage_type)
);

CREATE INDEX idx_caregiver_linkage_caregiver ON vito.caregiver_linkages (tenant_id, caregiver_health_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_caregiver_linkage_subject ON vito.caregiver_linkages (tenant_id, subject_health_id)
    WHERE status = 'ACTIVE';

-- Outbox event for linkage changes
INSERT INTO vito.event_outbox (aggregate_type, aggregate_id, event_type, schema_version, correlation_id, producer, tenant_id, subject_type, payload)
VALUES ('SCHEMA', 'V020', 'SCHEMA_MIGRATION', 1, gen_random_uuid()::text, 'flyway', 'system', 'caregiver_linkages',
        '{"migration": "V020", "description": "Caregiver linkage model"}'::jsonb);
