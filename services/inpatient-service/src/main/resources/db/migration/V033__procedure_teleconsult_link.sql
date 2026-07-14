-- Wave 4 T&L §6a — tele-PACU / tele-ICU support link.
-- A deteriorating recovery patient can be linked to a PCT teleconsult session (a "PCT referral" IS the
-- teleconsult session — session id = referral id). PCT owns the session; inpatient only records the ref
-- + purpose (POSTOP_SUPPORT). Orchestration-by-reference: no duplicate teleconsult record.
CREATE TABLE IF NOT EXISTS inpatient.procedure_teleconsult_link (
    link_id           UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    episode_id        UUID          NOT NULL
        REFERENCES inpatient.procedure_episode (episode_id) ON DELETE CASCADE,
    pct_session_id    VARCHAR(128),
    purpose           VARCHAR(32)   NOT NULL DEFAULT 'POSTOP_SUPPORT',
    session_mode      VARCHAR(32),
    status            VARCHAR(24)   NOT NULL DEFAULT 'REQUESTED',
    idempotency_key   VARCHAR(160),
    requested_by      VARCHAR(128),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_teleconsult_link_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_teleconsult_link_episode
    ON inpatient.procedure_teleconsult_link (episode_id);
