-- FCV-W1 — the Ministry legitimacy decision: the one governed act that may say a facility is
-- legitimate, and the only thing that may grant it platform operation.
--
-- Why this table exists rather than another column on the facility. Both importers unconditionally
-- stamp PLATFORM_OPERATIONAL / PENDING_VERIFICATION / allowed=false, so no import can ever produce
-- operational=true — the veto is structural. What was missing was the act that lifts it: a record of
-- WHO decided, under WHAT authority, in WHICH jurisdiction, on WHAT evidence, with WHAT conditions,
-- and until WHEN. A boolean cannot carry that, and `facility_source_legitimacy` cannot either: it is
-- UNIQUE (facility_id, source), one current row per source, no history.
--
-- So: this table is the system of record and is APPEND-ONLY (a later decision supersedes an earlier
-- one; nothing is updated in place except stamping superseded_by). The legitimacy rows the veto
-- lattice reads are its derived projection, bound back by facility_source_legitimacy.decision_id
-- (V044) so no import can overwrite them.

CREATE TABLE IF NOT EXISTS tuso.facility_legitimacy_decision (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_uuid       UUID NOT NULL,

    -- ── the decision ──────────────────────────────────────────────────────────────────────────
    -- verdict = the legitimacy judgement. disposition = what became of the record as an identity.
    -- They are separate columns deliberately: MERGED and DUPLICATE are identity-lifecycle outcomes,
    -- not statements about whether a facility is legitimate, and collapsing them into one vocabulary
    -- would leave a merged facility's appointments and cases dangling behind a "deny" with no
    -- redirection.
    verdict             VARCHAR(48) NOT NULL,
    disposition         VARCHAR(32),
    superseded_facility_uuid UUID,          -- survivor, when disposition is MERGED/DUPLICATE

    -- ── the authority ─────────────────────────────────────────────────────────────────────────
    decided_by          VARCHAR(64) NOT NULL,       -- person Health ID
    decided_by_role     VARCHAR(64) NOT NULL,       -- MOHCC_*_VERIFIER, from the appointment
    jurisdiction_code   VARCHAR(48) NOT NULL,       -- the appointment's jurisdiction at decision time
    ministry_authority  VARCHAR(128),               -- statutory basis / instrument, when cited

    -- ── the basis ─────────────────────────────────────────────────────────────────────────────
    decision_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    evidence_refs       JSONB NOT NULL DEFAULT '[]'::jsonb,
    mfl_source_ref      VARCHAR(255),
    inspection_ref      VARCHAR(255),
    verification_case_ref VARCHAR(64),              -- the proof-of-place case this rests on, if any
    reason              TEXT NOT NULL,

    -- ── the limits ────────────────────────────────────────────────────────────────────────────
    conditions          TEXT,
    restrictions        TEXT,
    review_date         DATE,
    expires_at          DATE,

    -- ── lineage ───────────────────────────────────────────────────────────────────────────────
    superseded_by       UUID REFERENCES tuso.facility_legitimacy_decision (id),
    superseded_at       TIMESTAMPTZ,
    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_facility_legitimacy_verdict CHECK (verdict IN (
        'LEGITIMATE_OPERATIONAL',
        'LEGITIMATE_CONDITIONALLY_OPERATIONAL',
        'LEGITIMATE_NOT_OPERATIONAL',
        'PENDING_VERIFICATION',
        'SUSPENDED',
        'REVOKED',
        'CLOSED',
        'REJECTED')),

    CONSTRAINT chk_facility_legitimacy_disposition CHECK (disposition IS NULL OR disposition IN (
        'DUPLICATE', 'MERGED')),

    -- A conditional verdict without its conditions is not a conditional verdict.
    CONSTRAINT chk_facility_legitimacy_conditions CHECK (
        verdict <> 'LEGITIMATE_CONDITIONALLY_OPERATIONAL'
        OR (conditions IS NOT NULL AND btrim(conditions) <> '')),

    -- An identity disposition must name the survivor, or the loser's records dangle.
    CONSTRAINT chk_facility_legitimacy_survivor CHECK (
        disposition IS NULL OR superseded_facility_uuid IS NOT NULL)
);

-- The current decision for a facility: the one not yet superseded. Partial-unique so the
-- append-only history can hold many, but exactly one stands.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_legitimacy_decision_current
    ON tuso.facility_legitimacy_decision (facility_uuid)
    WHERE superseded_by IS NULL;

CREATE INDEX IF NOT EXISTS idx_facility_legitimacy_decision_facility
    ON tuso.facility_legitimacy_decision (facility_uuid, created_at DESC);

-- Drives the expiry/review sweep: a decision that grants operation and then lapses must not leave
-- the allow standing forever.
CREATE INDEX IF NOT EXISTS idx_facility_legitimacy_decision_expiry
    ON tuso.facility_legitimacy_decision (expires_at)
    WHERE superseded_by IS NULL AND expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_facility_legitimacy_decision_review
    ON tuso.facility_legitimacy_decision (review_date)
    WHERE superseded_by IS NULL AND review_date IS NOT NULL;

COMMENT ON TABLE tuso.facility_legitimacy_decision IS
    'Append-only system of record for Ministry facility legitimacy decisions (FCV-W1). The rows in '
    'facility_source_legitimacy carrying decision_id are its derived projection — this table is the '
    'truth, that one is the index the veto lattice reads.';
