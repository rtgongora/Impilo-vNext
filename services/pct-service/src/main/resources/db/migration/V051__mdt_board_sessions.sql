-- TM-B15 (B15-1): MDT / specialist-board spine (spec profile #9: "PCT case + MDT record" SoR).
-- A board session is a chaired review of a case agenda. Each case item points at a real referral
-- (the case being presented), carries its recommendation + per-participant consensus/dissent
-- positions, and on close writes the consensus package back onto the referral so the referral
-- lifecycle stays canonical. Identity visibility is a session-level policy enforced SERVER-SIDE
-- at the read model (pseudonymised presentation — acceptance criterion), not in the UI.
CREATE TABLE IF NOT EXISTS pct_mdt_sessions (
    mdt_session_id      UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    title               VARCHAR(200),
    chair_id            VARCHAR(128) NOT NULL,
    scribe_id           VARCHAR(128),
    specialty           VARCHAR(120),
    routing_pool_id     VARCHAR(120),          -- panel membership discovery = vashandi pool (trauma precedent)
    rtc_session_id      VARCHAR(64),           -- set when a live MDT room is provisioned (template mode MDT)
    identity_visibility VARCHAR(32) NOT NULL DEFAULT 'PSEUDONYMISED',
    status              VARCHAR(24) NOT NULL DEFAULT 'PLANNED',   -- PLANNED | IN_SESSION | CLOSED
    scheduled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_pct_mdt_sessions_tenant_status
    ON pct_mdt_sessions (tenant_id, status, scheduled_at);

CREATE TABLE IF NOT EXISTS pct_mdt_case_items (
    case_item_id    UUID PRIMARY KEY,
    mdt_session_id  UUID NOT NULL REFERENCES pct_mdt_sessions (mdt_session_id),
    tenant_id       UUID NOT NULL,
    ordinal         INT NOT NULL,               -- agenda order; also the pseudonym ("Case 1", "Case 2"…)
    referral_id     UUID NOT NULL,              -- the presented case (pct_referrals)
    presenter_id    VARCHAR(128),
    status          VARCHAR(24) NOT NULL DEFAULT 'PENDING',       -- PENDING | PRESENTED | CLOSED
    recommendation  TEXT,                       -- board recommendation (recommendations only — no orders)
    positions       JSONB NOT NULL DEFAULT '[]',-- [{participant, position AGREE|DISSENT, note}] consensus + dissent
    recorded_by     VARCHAR(128),               -- chair/scribe who recorded the outcome
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_pct_mdt_case_items_session
    ON pct_mdt_case_items (mdt_session_id, ordinal);
CREATE INDEX IF NOT EXISTS ix_pct_mdt_case_items_referral
    ON pct_mdt_case_items (tenant_id, referral_id);
