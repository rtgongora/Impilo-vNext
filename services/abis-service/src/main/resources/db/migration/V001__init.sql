-- ABIS — Biometric Template Custody & Matching: Base schema
-- Encrypted biometric template custody and event outbox (Identity Contract §11, D6).
-- Deploy queues for the next authorized full-boot (new service).

CREATE SCHEMA IF NOT EXISTS abis;

-- ============================================================================
-- Templates — encrypted biometric templates keyed by opaque subject_ref.
-- subject_ref is NEVER a Health ID: TSHEPO holds the biometric-subject → HID
-- mapping; ABIS stores no demographics and no PII.
-- ============================================================================
CREATE TABLE abis.template (
    id                   BIGSERIAL    PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    subject_ref          VARCHAR(128) NOT NULL,
    modality             VARCHAR(32)  NOT NULL,
    position             VARCHAR(32)  NOT NULL DEFAULT 'PRIMARY',
    quality_score        INTEGER,
    algorithm_version    VARCHAR(64),
    template_encrypted   BYTEA        NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_template_subject UNIQUE (tenant_id, subject_ref, modality, position)
);

CREATE INDEX idx_template_tenant_subject ON abis.template (tenant_id, subject_ref, status);
CREATE INDEX idx_template_tenant_modality ON abis.template (tenant_id, modality, status);

-- ============================================================================
-- Event Outbox — reliable Kafka publishing (standard Impilo outbox pattern)
-- ============================================================================
CREATE TABLE abis.event_outbox (
    id                   BIGSERIAL    PRIMARY KEY,
    aggregate_type       VARCHAR(255) NOT NULL,
    aggregate_id         VARCHAR(255) NOT NULL,
    event_type           VARCHAR(255) NOT NULL,
    payload              JSONB        NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at         TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON abis.event_outbox (created_at ASC) WHERE published_at IS NULL;
