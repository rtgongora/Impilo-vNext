-- Persist provider biometric verification outcomes (parity with VITO verification events).

CREATE TABLE varapi.provider_biometric_verification_event (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    provider_id        BIGINT      NOT NULL REFERENCES varapi.provider (id),
    profile_id         UUID REFERENCES varapi.provider_biometric_profile (profile_id),
    workflow_type      VARCHAR(64) NOT NULL,
    modality           VARCHAR(20),
    requested_by       VARCHAR(255) NOT NULL,
    result               VARCHAR(32) NOT NULL,
    confidence_score   DOUBLE PRECISION,
    liveness_outcome   VARCHAR(64),
    decision_summary   VARCHAR(512),
    policy_rule_id     BIGINT,
    policy_outcome     VARCHAR(32),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_bio_verify_tenant_provider
    ON varapi.provider_biometric_verification_event (tenant_id, provider_id, created_at DESC);
