-- =============================================
-- Service : tshepo-authz-service
-- Flyway  : V016__step_up_totp_enrolment.sql
-- Purpose : Authenticator-app TOTP enrolment store (RFC 6238) for step-up.
--           The RFC 6238 verifier already exists; this persists each actor's enrolled
--           shared secret (AES-256-GCM encrypted at rest) so TOTP step-up can complete.
-- One enrolment per (tenant, actor); re-enrolment overwrites/revokes the prior secret.
-- =============================================

CREATE TABLE tshepo_authz.totp_enrolment (
    tenant_id      UUID         NOT NULL,
    actor_id       VARCHAR(128) NOT NULL,
    secret_cipher  TEXT         NOT NULL,   -- base64(AES-256-GCM ciphertext of the raw HMAC secret)
    secret_iv      TEXT         NOT NULL,   -- base64(12-byte GCM IV), unique per record
    algorithm      VARCHAR(16)  NOT NULL DEFAULT 'SHA1',
    digits         INT          NOT NULL DEFAULT 6,
    period         INT          NOT NULL DEFAULT 30,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING_CONFIRM',  -- PENDING_CONFIRM, ACTIVE, REVOKED
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, actor_id)
);

CREATE INDEX idx_totp_enrolment_status
    ON tshepo_authz.totp_enrolment (tenant_id, status);
