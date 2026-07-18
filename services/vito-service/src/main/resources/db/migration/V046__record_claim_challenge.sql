-- Identity Journey Doctrine §2 / Wave F (CJ3): "claim my record" second factor.
-- A short-lived OTP challenge dispatched to the contact ALREADY RECORDED on the
-- resolved Health ID — the claimant must prove possession of something already
-- linked to the record before an account is bound to it.
CREATE TABLE IF NOT EXISTS vito.record_claim_challenge (
    id               BIGSERIAL     PRIMARY KEY,
    challenge_id     UUID          NOT NULL UNIQUE,
    tenant_id        UUID          NOT NULL,
    client_health_id UUID          NOT NULL,
    otp_hash         TEXT          NOT NULL,
    contact_masked   VARCHAR(64),
    channel          VARCHAR(16)   NOT NULL DEFAULT 'phone',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING | VERIFIED | EXPIRED | LOCKED
    attempts         INT           NOT NULL DEFAULT 0,
    max_attempts     INT           NOT NULL DEFAULT 5,
    expires_at       TIMESTAMPTZ   NOT NULL,
    verified_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_record_claim_challenge_lookup ON vito.record_claim_challenge (challenge_id);
COMMENT ON TABLE vito.record_claim_challenge IS 'Second-factor OTP for citizen record claim (Identity Journey Doctrine §2)';
