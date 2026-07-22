-- =====================================================================================
-- varapi :: V034 — Disciplinary proceeding FSM + rito-referral firewall (ROM-W7, R5)
-- Gives provider_disciplinary_cases (V005) a real state machine and the human-mediated referral
-- link. A rito complaint NEVER auto-transitions a proceeding: source_rito_case_id is set only when
-- an appointed officer opens a case (the firewall). Determinations write first-class restriction
-- rows (V029), never a silent registration mutation.
-- =====================================================================================

ALTER TABLE varapi.provider_disciplinary_cases
    ADD COLUMN IF NOT EXISTS case_stage         VARCHAR(40) NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN IF NOT EXISTS council_id         BIGINT REFERENCES varapi.councils(id),
    ADD COLUMN IF NOT EXISTS source_rito_case_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS opened_by          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS respondent_notified BOOLEAN NOT NULL DEFAULT false;

-- The FSM: RECEIVED → PRELIMINARY_ASSESSMENT → UNDER_INVESTIGATION → CHARGES_FORMULATED →
--          REFERRED_TO_COMMITTEE → HEARING → DETERMINED → APPEALED | CLOSED
ALTER TABLE varapi.provider_disciplinary_cases
    DROP CONSTRAINT IF EXISTS chk_disciplinary_stage;
ALTER TABLE varapi.provider_disciplinary_cases
    ADD CONSTRAINT chk_disciplinary_stage CHECK (case_stage IN
        ('RECEIVED', 'PRELIMINARY_ASSESSMENT', 'UNDER_INVESTIGATION', 'CHARGES_FORMULATED',
         'REFERRED_TO_COMMITTEE', 'HEARING', 'DETERMINED', 'APPEALED', 'CLOSED'));

CREATE INDEX IF NOT EXISTS idx_disciplinary_stage
    ON varapi.provider_disciplinary_cases(tenant_id, case_stage);
CREATE INDEX IF NOT EXISTS idx_disciplinary_rito_source
    ON varapi.provider_disciplinary_cases(source_rito_case_id) WHERE source_rito_case_id IS NOT NULL;
