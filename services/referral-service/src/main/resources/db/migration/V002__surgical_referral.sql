-- referral-service V002 — surgical referral companion (Wave 2 elective theatre)
-- 1:1 with referral.referrals(id). The surgical referral carries the clinical
-- indication + decision for the receiving surgical team. referral.surgical.accepted
-- is the ONLY trigger that funnels an elective case into scheduling (waitlist).
-- The referral itself remains the source of truth for the referral lifecycle FSM;
-- this table adds the surgical-domain depth as a companion, never a copy.

CREATE TABLE referral.surgical_referral (
    id                              UUID            PRIMARY KEY,
    referral_id                     UUID            NOT NULL UNIQUE
        REFERENCES referral.referrals(id) ON DELETE CASCADE,
    tenant_id                       UUID            NOT NULL,
    patient_id                      VARCHAR(255)    NOT NULL,
    indication                      TEXT,
    intended_procedure_zibo_code    VARCHAR(64),
    urgency                         VARCHAR(24)     NOT NULL DEFAULT 'ROUTINE',
    clinical_priority               VARCHAR(8),     -- P1 | P2 | P3 | P4
    laterality                      VARCHAR(16),    -- LEFT | RIGHT | BILATERAL | NOT_APPLICABLE
    anatomical_site                 VARCHAR(128),
    requested_investigations        JSONB           NOT NULL DEFAULT '[]'::jsonb,
    anaesthesia_review_requested    BOOLEAN         NOT NULL DEFAULT FALSE,
    specialist_review_requested     BOOLEAN         NOT NULL DEFAULT FALSE,
    target_specialty                VARCHAR(64),
    decision                        VARCHAR(24)     NOT NULL DEFAULT 'PENDING',
        -- PENDING | ACCEPTED | DECLINED | REDIRECTED | INFO_REQUESTED
    decision_reason                 TEXT,
    waitlist_entry_ref              VARCHAR(64),    -- scheduling.surgical_waitlist_entry id (reference, not copy)
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_surgical_referral_decision
        CHECK (decision IN ('PENDING','ACCEPTED','DECLINED','REDIRECTED','INFO_REQUESTED'))
);

CREATE INDEX idx_surgical_referral_tenant_decision
    ON referral.surgical_referral (tenant_id, decision, updated_at DESC);
CREATE INDEX idx_surgical_referral_tenant_specialty
    ON referral.surgical_referral (tenant_id, target_specialty, decision);

-- Partial-unique: only one ACTIVE surgical referral per (tenant, patient, procedure).
-- ACTIVE = not yet terminally declined/redirected. Prevents duplicate listings.
CREATE UNIQUE INDEX uq_surgical_referral_active
    ON referral.surgical_referral (tenant_id, patient_id, intended_procedure_zibo_code)
    WHERE decision IN ('PENDING','INFO_REQUESTED','ACCEPTED')
      AND intended_procedure_zibo_code IS NOT NULL;
