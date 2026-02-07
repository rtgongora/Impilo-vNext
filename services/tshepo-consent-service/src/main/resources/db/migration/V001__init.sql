-- ============================================================================
-- TSHEPO Consent & Preference Management Service — Initial Schema
-- Schema: tshepo_consent
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS tshepo_consent;

-- ---------------------------------------------------------------------------
-- consent_directive: Core consent record storing FHIR R4 Consent as JSONB
-- ---------------------------------------------------------------------------
CREATE TABLE tshepo_consent.consent_directive (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    patient_ref       VARCHAR(255) NOT NULL,
    grantor_ref       VARCHAR(255) NOT NULL,
    grantee_ref       VARCHAR(255),
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    scope             VARCHAR(64)  NOT NULL,
    purpose           VARCHAR(64)  NOT NULL,
    provision         VARCHAR(8)   NOT NULL DEFAULT 'permit',
    fhir_consent_json JSONB        NOT NULL,
    period_start      TIMESTAMPTZ,
    period_end        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    revocation_reason TEXT
);

COMMENT ON TABLE tshepo_consent.consent_directive IS
    'Patient consent directives stored as FHIR R4 Consent resources. No PII — only opaque CPID/HealthID refs.';

-- Composite index for patient consent lookups (hot path for evaluation)
CREATE INDEX idx_consent_patient
    ON tshepo_consent.consent_directive (tenant_id, patient_ref, status);

-- Composite index for grantee-based consent lookups (evaluation by actor)
CREATE INDEX idx_consent_grantee
    ON tshepo_consent.consent_directive (tenant_id, grantee_ref, status);

-- Index for evaluation queries that filter on purpose and provision
CREATE INDEX idx_consent_eval
    ON tshepo_consent.consent_directive (tenant_id, patient_ref, grantee_ref, purpose, status, provision);

-- ---------------------------------------------------------------------------
-- consent_audit: Immutable audit trail for all consent mutations
-- ---------------------------------------------------------------------------
CREATE TABLE tshepo_consent.consent_audit (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    consent_id  UUID         NOT NULL REFERENCES tshepo_consent.consent_directive(id),
    action      VARCHAR(32)  NOT NULL,
    actor_id    VARCHAR(255) NOT NULL,
    detail      JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE tshepo_consent.consent_audit IS
    'Immutable audit log for consent lifecycle events (create, update, revoke, evaluate).';

CREATE INDEX idx_audit_consent
    ON tshepo_consent.consent_audit (consent_id);

CREATE INDEX idx_audit_tenant_time
    ON tshepo_consent.consent_audit (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- share_link: Short-lived access tokens for patient-delegated data sharing
-- ---------------------------------------------------------------------------
CREATE TABLE tshepo_consent.share_link (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    patient_ref VARCHAR(255) NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    scope       VARCHAR(64)  NOT NULL,
    purpose     VARCHAR(64)  NOT NULL,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    max_uses    INT          NOT NULL DEFAULT 1,
    use_count   INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ
);

COMMENT ON TABLE tshepo_consent.share_link IS
    'Short-lived share links for patient-delegated data sharing. Token stored as SHA-256 hash only.';

CREATE INDEX idx_share_link_token
    ON tshepo_consent.share_link (token_hash);

CREATE INDEX idx_share_link_patient
    ON tshepo_consent.share_link (tenant_id, patient_ref);

-- ---------------------------------------------------------------------------
-- event_outbox: Transactional outbox for reliable Kafka event publishing
-- ---------------------------------------------------------------------------
CREATE TABLE tshepo_consent.event_outbox (
    id             BIGSERIAL    PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

COMMENT ON TABLE tshepo_consent.event_outbox IS
    'Transactional outbox table for reliable Kafka event delivery. Polled by ConsentOutboxPublisher.';

CREATE INDEX idx_outbox_unpublished
    ON tshepo_consent.event_outbox (created_at ASC)
    WHERE published_at IS NULL;
