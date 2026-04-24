-- Patient-mediated governed sharing with external / not-yet-onboarded providers.

CREATE TABLE vito.patient_share_request (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    health_id           UUID         NOT NULL,
    initiated_by        VARCHAR(255) NOT NULL,
    share_type          VARCHAR(32)  NOT NULL DEFAULT 'COLLABORATION',
    target_provider_hint VARCHAR(512),
    purpose_of_use      VARCHAR(64),
    scope_type          VARCHAR(64)  NOT NULL,
    scope_summary       TEXT,
    share_status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revocable_flag      BOOLEAN      NOT NULL DEFAULT true,
    notes               TEXT,
    correlation_id      VARCHAR(64)
);

CREATE INDEX idx_psr_tenant_health ON vito.patient_share_request (tenant_id, health_id);

CREATE TABLE vito.patient_share_artifact (
    id                  BIGSERIAL PRIMARY KEY,
    share_request_id    BIGINT       NOT NULL REFERENCES vito.patient_share_request(id),
    artifact_type       VARCHAR(32)  NOT NULL DEFAULT 'LINK',
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    otp_hash            VARCHAR(255) NOT NULL,
    active_flag         BOOLEAN      NOT NULL DEFAULT true,
    one_time_use_flag   BOOLEAN      NOT NULL DEFAULT true,
    issued_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    otp_attempts        INT          NOT NULL DEFAULT 0,
    max_otp_attempts    INT          NOT NULL DEFAULT 5,
    use_count           INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_psa_request ON vito.patient_share_artifact (share_request_id);

CREATE TABLE vito.patient_share_access_grant (
    id                          BIGSERIAL PRIMARY KEY,
    share_request_id            BIGINT       NOT NULL REFERENCES vito.patient_share_request(id),
    tenant_id                   UUID         NOT NULL,
    health_id                   UUID         NOT NULL,
    scope_type                  VARCHAR(64)  NOT NULL,
    allowed_actions             TEXT,
    visibility_profile          VARCHAR(64),
    update_permissions          TEXT,
    issued_by_policy_ref       VARCHAR(128),
    status                      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    trust_level                 VARCHAR(64),
    external_profile_id         BIGINT,
    temporary_provider_public_id VARCHAR(26),
    start_at                    TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ  NOT NULL,
    revoked_at                  TIMESTAMPTZ,
    session_activated_count     INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_psg_request ON vito.patient_share_access_grant (share_request_id);
CREATE INDEX idx_psg_tenant_health ON vito.patient_share_access_grant (tenant_id, health_id);

CREATE TABLE vito.patient_share_session (
    id                  BIGSERIAL PRIMARY KEY,
    access_grant_id     BIGINT       NOT NULL REFERENCES vito.patient_share_access_grant(id),
    session_token_hash  VARCHAR(255) NOT NULL UNIQUE,
    external_profile_id BIGINT,
    session_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING_OTP',
    opened_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_active_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ
);

CREATE INDEX idx_pss_grant ON vito.patient_share_session (access_grant_id);

CREATE TABLE vito.patient_share_contribution (
    id                          BIGSERIAL PRIMARY KEY,
    access_grant_id             BIGINT       NOT NULL REFERENCES vito.patient_share_access_grant(id),
    session_id                   BIGINT       REFERENCES vito.patient_share_session(id),
    external_profile_id          BIGINT,
    temporary_provider_public_id VARCHAR(26),
    target_record_type           VARCHAR(64)  NOT NULL,
    target_record_id             VARCHAR(255),
    action_type                  VARCHAR(64)  NOT NULL,
    summary                      TEXT,
    payload                      JSONB,
    trust_level_at_contribution  VARCHAR(64),
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_psc_grant ON vito.patient_share_contribution (access_grant_id);

CREATE TABLE vito.patient_share_revocation (
    id                  BIGSERIAL PRIMARY KEY,
    share_request_id    BIGINT REFERENCES vito.patient_share_request(id),
    access_grant_id     BIGINT REFERENCES vito.patient_share_access_grant(id),
    revoked_by          VARCHAR(255) NOT NULL,
    reason              TEXT,
    revoked_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
