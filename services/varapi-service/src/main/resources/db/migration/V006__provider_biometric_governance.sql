-- Provider biometric governance (VARAPI — workforce identity).

CREATE TABLE varapi.provider_biometric_profile (
    profile_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    provider_id       BIGINT      NOT NULL REFERENCES varapi.provider (id),
    biometric_status  VARCHAR(32) NOT NULL DEFAULT 'NONE',
    assurance_level   INTEGER,
    enrolled_by       VARCHAR(255),
    source_context    VARCHAR(255),
    active_flag       BOOLEAN     NOT NULL DEFAULT true,
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_biometric_profile UNIQUE (tenant_id, provider_id)
);

CREATE INDEX idx_provider_biometric_profile_tenant ON varapi.provider_biometric_profile (tenant_id);

CREATE TABLE varapi.provider_biometric_template (
    id                 BIGSERIAL PRIMARY KEY,
    profile_id         UUID REFERENCES varapi.provider_biometric_profile (profile_id),
    tenant_id          UUID        NOT NULL,
    provider_id        BIGINT      NOT NULL REFERENCES varapi.provider (id),
    modality           VARCHAR(20) NOT NULL,
    position           VARCHAR(64),
    template_hash      VARCHAR(64) NOT NULL,
    template_data      BYTEA       NOT NULL,
    quality_score      INTEGER,
    capture_device     VARCHAR(255),
    captured_by        VARCHAR(255) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    captured_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    superseded_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    vendor_engine      VARCHAR(64),
    liveness_supported BOOLEAN     NOT NULL DEFAULT false
);

CREATE INDEX idx_provider_bio_template_tenant_modality
    ON varapi.provider_biometric_template (tenant_id, modality, status);
