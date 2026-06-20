-- referral-service V001 — tenant-scoped referrals + transactional outbox
CREATE SCHEMA IF NOT EXISTS referral;

CREATE TABLE referral.referrals (
    id              UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    patient_id      VARCHAR(255)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    payload         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    response_notes  TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_referral_tenant_patient ON referral.referrals (tenant_id, patient_id, created_at DESC);
CREATE INDEX idx_referral_tenant_status ON referral.referrals (tenant_id, status, updated_at DESC);

CREATE TABLE referral.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_referral_outbox_unpublished
    ON referral.event_outbox (created_at)
    WHERE published_at IS NULL;
