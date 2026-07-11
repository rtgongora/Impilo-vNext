-- =============================================================================
-- Varapi V023 — PIC eligibility assessment snapshot (HPA regulatory platform).
--
-- Point-in-time, auditable record of a practitioner-in-charge eligibility
-- assessment. Each row captures the FULL per-axis credential picture (identity,
-- council, registration, practising certificate, licence, restrictions) as it
-- was evaluated at a given `as_of` date — unifying the fragmented credential
-- truth (registration records, certificates, licences, disciplinary actions,
-- council licence records) into one governed decision artefact that the
-- facility-side nomination workflow can reference forever.
-- =============================================================================

CREATE TABLE IF NOT EXISTS varapi.provider_eligibility_snapshot (
    snapshot_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID,
    provider_id        BIGINT        NOT NULL,
    provider_public_id VARCHAR(64)   NOT NULL,
    impilo_health_id   UUID,
    facility_ref       VARCHAR(128),
    facility_unit_ref  VARCHAR(128),
    purpose            VARCHAR(64)   NOT NULL DEFAULT 'PIC_NOMINATION',
    as_of              DATE          NOT NULL,
    assessed_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    eligible           BOOLEAN       NOT NULL,
    result             VARCHAR(32)   NOT NULL,      -- ELIGIBLE | INELIGIBLE | CONDITIONAL
    axes               JSONB         NOT NULL,      -- per-axis assessment + evidence pointers
    reasons            JSONB         NOT NULL,      -- human-readable reason strings
    requested_by       VARCHAR(255),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_provider_eligibility_snapshot_provider_public_id
    ON varapi.provider_eligibility_snapshot (provider_public_id);

CREATE INDEX IF NOT EXISTS idx_provider_eligibility_snapshot_facility_ref
    ON varapi.provider_eligibility_snapshot (facility_ref);
