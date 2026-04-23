-- Biometric governance extensions: profile linkage, auditable events, exceptions.

CREATE TABLE vito.biometric_profile (
    profile_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    subject_type      VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    health_id         UUID        NOT NULL REFERENCES vito.client (health_id),
    biometric_status  VARCHAR(32) NOT NULL DEFAULT 'NONE',
    assurance_level   INTEGER,
    enrolled_by       VARCHAR(255),
    source_context    VARCHAR(255),
    active_flag       BOOLEAN     NOT NULL DEFAULT true,
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_biometric_profile_tenant_client UNIQUE (tenant_id, health_id)
);

CREATE INDEX idx_biometric_profile_tenant ON vito.biometric_profile (tenant_id);

CREATE TABLE vito.biometric_enrollment_event (
    id                 BIGSERIAL PRIMARY KEY,
    profile_id         UUID        NOT NULL REFERENCES vito.biometric_profile (profile_id),
    subject_type       VARCHAR(20) NOT NULL,
    health_id          UUID        NOT NULL,
    workflow_type      VARCHAR(64) NOT NULL,
    enrolled_by        VARCHAR(255),
    enrollment_context VARCHAR(128),
    device_ref         VARCHAR(255),
    quality_outcome    VARCHAR(64),
    consent_ref        VARCHAR(255),
    authorization_ref  VARCHAR(255),
    policy_rule_id     BIGINT,
    policy_outcome     VARCHAR(32),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_biometric_enrollment_profile ON vito.biometric_enrollment_event (profile_id);

CREATE TABLE vito.biometric_verification_event (
    id                 BIGSERIAL PRIMARY KEY,
    profile_id         UUID REFERENCES vito.biometric_profile (profile_id),
    health_id          UUID,
    workflow_type      VARCHAR(64) NOT NULL,
    requested_by       VARCHAR(255) NOT NULL,
    result             VARCHAR(32)  NOT NULL,
    confidence_score   DOUBLE PRECISION,
    liveness_outcome   VARCHAR(64),
    decision_summary   VARCHAR(512),
    policy_rule_id     BIGINT,
    policy_outcome     VARCHAR(32),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_biometric_verify_health ON vito.biometric_verification_event (health_id);

CREATE TABLE vito.biometric_identification_event (
    id                 BIGSERIAL PRIMARY KEY,
    subject_type       VARCHAR(20) NOT NULL,
    workflow_type      VARCHAR(64) NOT NULL,
    requested_by       VARCHAR(255) NOT NULL,
    candidate_count    INTEGER     NOT NULL DEFAULT 0,
    top_match_health_id UUID,
    confidence_score   DOUBLE PRECISION,
    result             VARCHAR(32)  NOT NULL,
    decision_summary   VARCHAR(512),
    policy_rule_id     BIGINT,
    policy_outcome     VARCHAR(32),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vito.biometric_exception_record (
    id                 BIGSERIAL PRIMARY KEY,
    subject_type       VARCHAR(20) NOT NULL,
    health_id          UUID,
    workflow_type      VARCHAR(64) NOT NULL,
    exception_type     VARCHAR(64) NOT NULL,
    reason             VARCHAR(1024),
    fallback_used      BOOLEAN     NOT NULL DEFAULT false,
    created_by         VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE vito.biometric_template
    ADD COLUMN profile_id UUID REFERENCES vito.biometric_profile (profile_id),
    ADD COLUMN vendor_engine VARCHAR(64),
    ADD COLUMN liveness_supported BOOLEAN NOT NULL DEFAULT false;

-- Backfill profiles for existing templates
INSERT INTO vito.biometric_profile (profile_id, tenant_id, subject_type, health_id, biometric_status, active_flag)
SELECT gen_random_uuid(), d.tenant_id, 'CLIENT', d.health_id, 'ENROLLED', true
FROM (SELECT DISTINCT tenant_id, health_id FROM vito.biometric_template) d
WHERE NOT EXISTS (
    SELECT 1 FROM vito.biometric_profile p
    WHERE p.tenant_id = d.tenant_id AND p.health_id = d.health_id
);

UPDATE vito.biometric_template t
SET profile_id = p.profile_id
FROM vito.biometric_profile p
WHERE p.tenant_id = t.tenant_id
  AND p.health_id = t.health_id
  AND t.profile_id IS NULL;
