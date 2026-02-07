-- TSHEPO — Consent records

CREATE TABLE tshepo.consent_directive (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    subject_cpid    VARCHAR(64)  NOT NULL,
    grantor_id      VARCHAR(255) NOT NULL,
    grantee_scope   VARCHAR(255) NOT NULL,
    purpose_of_use  VARCHAR(100) NOT NULL,
    resource_scope  VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, REVOKED, EXPIRED
    valid_from      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    valid_to        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    provenance      JSONB
);

CREATE INDEX idx_consent_subject ON tshepo.consent_directive (tenant_id, subject_cpid, status);
CREATE INDEX idx_consent_grantee ON tshepo.consent_directive (tenant_id, grantee_scope, status);
