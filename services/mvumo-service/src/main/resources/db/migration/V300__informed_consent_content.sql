-- =====================================================================================
-- MVUMO :: V300 — informed consent is the record of a conversation (pipeline §6)
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3).
--
-- What exists is genuinely good and is not being rebuilt: consent_request with state,
-- method, assurance, proof_ref and refusal reason; separate procedure, anaesthesia and
-- transfusion consents in the perioperative bundle (V007); an audited emergency-exception
-- path with two-doctor, deferred and proxy variants; theatre start hard-gated on GRANTED.
--
-- What is missing is the CONTENT of the conversation. The record says consent happened.
-- It does not say what was explained, what alternatives were offered, what the patient
-- asked, or in what language. That is exactly the reduction the specification forbids —
-- "consent is not just a signature", and a proof_ref pointing at an image is a signature
-- with extra steps.
--
-- The columns below are nullable because history cannot be retrofitted: consents already
-- granted in this estate have no recorded discussion, and inventing one would be worse
-- than admitting the gap. The enforcement is the completeness view at the bottom — the
-- pipeline can then require a COMPLETE consent for a procedure that warrants one, while
-- the record stays honest about which historical consents were never that.
-- =====================================================================================

ALTER TABLE mvumo.consent_request
    -- §6: what an informed consent must record.
    ADD COLUMN IF NOT EXISTS purpose_explained        TEXT,
    ADD COLUMN IF NOT EXISTS benefits_explained       TEXT,
    ADD COLUMN IF NOT EXISTS material_risks_explained TEXT,
    ADD COLUMN IF NOT EXISTS alternatives_explained   TEXT,
    ADD COLUMN IF NOT EXISTS consequences_of_declining TEXT,
    ADD COLUMN IF NOT EXISTS questions_asked          TEXT,
    ADD COLUMN IF NOT EXISTS language_code            VARCHAR(16),
    ADD COLUMN IF NOT EXISTS interpreter_used         BOOLEAN,
    ADD COLUMN IF NOT EXISTS interpreter_identity     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS obtained_by_ref          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS obtained_at              TIMESTAMPTZ,

    -- §6: capacity, assent and substitute decision-making as first-class facts rather than
    -- edge cases. capacity_assessed_outcome is deliberately three-valued: "not assessed" is
    -- a different fact from "has capacity", and defaulting the first to the second is how a
    -- consent taken from someone who could not give it looks valid.
    ADD COLUMN IF NOT EXISTS capacity_assessed        BOOLEAN,
    ADD COLUMN IF NOT EXISTS capacity_outcome         VARCHAR(24),
    -- HAS_CAPACITY | LACKS_CAPACITY | FLUCTUATING | NOT_ASSESSED
    ADD COLUMN IF NOT EXISTS decision_maker_type      VARCHAR(32),
    -- PATIENT | GUARDIAN | SUBSTITUTE_DECISION_MAKER | COURT_APPOINTED | EMERGENCY_EXCEPTION
    ADD COLUMN IF NOT EXISTS decision_maker_ref       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS decision_maker_basis     TEXT,

    -- Child assent, recorded separately from guardian consent because they are separate
    -- acts. A child who refuses assent has said something, and a model that stores only the
    -- guardian's answer cannot represent it.
    ADD COLUMN IF NOT EXISTS assent_sought            BOOLEAN,
    ADD COLUMN IF NOT EXISTS assent_outcome           VARCHAR(24),
    -- GIVEN | REFUSED | NOT_APPLICABLE | UNABLE
    ADD COLUMN IF NOT EXISTS assent_notes             TEXT,

    -- Withdrawal as an event with a time, not a state that erases what came before.
    ADD COLUMN IF NOT EXISTS withdrawn_at            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS withdrawn_by_ref        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS withdrawal_reason       TEXT,

    -- §6 separations that must not be inferred from a single grant.
    ADD COLUMN IF NOT EXISTS photography_consented   BOOLEAN,
    ADD COLUMN IF NOT EXISTS specimen_use_consented  BOOLEAN,
    ADD COLUMN IF NOT EXISTS research_use_consented  BOOLEAN;

ALTER TABLE mvumo.consent_request
    ADD CONSTRAINT chk_consent_capacity_outcome CHECK (
        capacity_outcome IS NULL OR capacity_outcome IN
            ('HAS_CAPACITY','LACKS_CAPACITY','FLUCTUATING','NOT_ASSESSED')),
    ADD CONSTRAINT chk_consent_decision_maker_type CHECK (
        decision_maker_type IS NULL OR decision_maker_type IN
            ('PATIENT','GUARDIAN','SUBSTITUTE_DECISION_MAKER','COURT_APPOINTED','EMERGENCY_EXCEPTION')),
    ADD CONSTRAINT chk_consent_assent_outcome CHECK (
        assent_outcome IS NULL OR assent_outcome IN ('GIVEN','REFUSED','NOT_APPLICABLE','UNABLE'));

-- A consent given by anyone other than the patient must say on what basis. "Guardian"
-- without a basis is an assertion of authority nobody recorded.
ALTER TABLE mvumo.consent_request
    ADD CONSTRAINT chk_consent_substitute_needs_basis CHECK (
        decision_maker_type IS NULL
        OR decision_maker_type IN ('PATIENT','EMERGENCY_EXCEPTION')
        OR (decision_maker_ref IS NOT NULL AND length(btrim(decision_maker_ref)) > 0
            AND decision_maker_basis IS NOT NULL AND length(btrim(decision_maker_basis)) > 0));

-- A withdrawal is an actor and a time together, or it did not happen.
ALTER TABLE mvumo.consent_request
    ADD CONSTRAINT chk_consent_withdrawal_pair CHECK (
        (withdrawn_at IS NULL AND withdrawn_by_ref IS NULL)
        OR (withdrawn_at IS NOT NULL AND withdrawn_by_ref IS NOT NULL));

-- If an interpreter was used, who it was must be recorded — otherwise the consent asserts a
-- language barrier was bridged by someone nobody can identify.
ALTER TABLE mvumo.consent_request
    ADD CONSTRAINT chk_consent_interpreter_identified CHECK (
        interpreter_used IS NOT TRUE
        OR (interpreter_identity IS NOT NULL AND length(btrim(interpreter_identity)) > 0));

-- -------------------------------------------------------------------------------------
-- Completeness, as a view rather than a column.
--
-- A stored "is_complete" flag would be a second copy of a fact the columns already carry,
-- and would drift the moment one of them changed. The view computes it, so a consent is
-- complete exactly when it actually is.
--
-- This is what lets the pipeline demand a genuinely informed consent for a procedure that
-- warrants one, without pretending that historical consents were ever that.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW mvumo.v_consent_completeness AS
SELECT
    r.id,
    r.tenant_id,
    r.subject_patient_ref,
    r.consent_type,
    r.state,
    (r.purpose_explained          IS NOT NULL AND length(btrim(r.purpose_explained)) > 0)          AS has_purpose,
    (r.benefits_explained         IS NOT NULL AND length(btrim(r.benefits_explained)) > 0)         AS has_benefits,
    (r.material_risks_explained   IS NOT NULL AND length(btrim(r.material_risks_explained)) > 0)   AS has_material_risks,
    (r.alternatives_explained     IS NOT NULL AND length(btrim(r.alternatives_explained)) > 0)     AS has_alternatives,
    (r.consequences_of_declining  IS NOT NULL AND length(btrim(r.consequences_of_declining)) > 0)  AS has_consequences,
    (r.obtained_by_ref            IS NOT NULL AND r.obtained_at IS NOT NULL)                       AS has_attribution,
    (r.language_code              IS NOT NULL)                                                     AS has_language,
    CASE WHEN r.purpose_explained IS NOT NULL AND length(btrim(r.purpose_explained)) > 0
          AND r.benefits_explained IS NOT NULL AND length(btrim(r.benefits_explained)) > 0
          AND r.material_risks_explained IS NOT NULL AND length(btrim(r.material_risks_explained)) > 0
          AND r.alternatives_explained IS NOT NULL AND length(btrim(r.alternatives_explained)) > 0
          AND r.consequences_of_declining IS NOT NULL AND length(btrim(r.consequences_of_declining)) > 0
          AND r.obtained_by_ref IS NOT NULL AND r.obtained_at IS NOT NULL
          AND r.language_code IS NOT NULL
         THEN 'INFORMED'
         WHEN r.proof_ref IS NOT NULL
         THEN 'SIGNATURE_ONLY'
         ELSE 'INCOMPLETE'
    END AS consent_depth
FROM mvumo.consent_request r;

COMMENT ON VIEW mvumo.v_consent_completeness IS
    'Consent depth: INFORMED (the conversation is recorded), SIGNATURE_ONLY (a grant with proof but no recorded discussion — the reduction pipeline §6 forbids), INCOMPLETE. Computed, never stored: a stored flag drifts from the columns it summarises.';
