-- =====================================================================================
-- Surgery :: V004 — the surgical decision-making record (S3, surgical domain pack §8)
--
-- The pack's own §8 names 18 elements, all ABSENT per its audit: diagnosis and certainty;
-- natural history; expected benefit; material risks; anaesthetic, blood, functional and
-- fertility implications; stoma possibility; implant possibility; rehabilitation expectation;
-- financial and access implications; patient preference; final decision; who decided and when.
--
-- Two duplication checks done before this migration, both element-by-element like S2:
--
--   1. Diagnosis and certainty are NOT re-stored here. surgery.surgical_episode.pct_problem_ref
--      (V002, S1) already resolves to the pct_problems row this service contributed with
--      responsible_service='surgery', which carries diagnostic_certainty. A second column here
--      would be exactly the "two records of one fact" duplication S1 refused for the same
--      table. Read the episode's own pct_problem_ref instead.
--
--   2. "Material risks" is NOT the same field as mvumo's consent_request.material_risks_explained
--      (V300, Wave P5) despite the similar name, and this migration deliberately does not
--      reference or duplicate that column. mvumo's field records what was EXPLAINED TO THE
--      PATIENT during the consent conversation. This table's material_risks_considered is the
--      SURGEON'S OWN clinical weighing that leads to recommending an operation in the first
--      place, which typically happens BEFORE that conversation and informs it. They are
--      related facts at different points in the same course of care, not one fact stored
--      twice — the same distinction S2 drew between differential_diagnosis_notes (working
--      reasoning) and pct_problems' formal DIFFERENTIAL certainty status.
--
-- "Final decision, who decided, when" is NOT PCT's admission-handshake decision (opening a bed
-- census) — a day-case procedure needs no inpatient admission at all. It is the surgical
-- decision to recommend/proceed with an operation, which ADR §5a explicitly permits this
-- service to own as "the surgical reasoning and the options considered." Component test
-- question 3 (execute, not decide) is satisfied because this is a clinical decision surgery
-- itself is authorised to make, distinct from PCT's phase-of-care decision.
--
-- One row per episode, refined over time like V003's assessment — until a final decision is
-- recorded, at which point who/when travel together or not at all (the same pairing idiom
-- mvumo's V300 uses for withdrawal).
-- =====================================================================================

CREATE TABLE surgery.surgical_decision (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    surgical_episode_id             UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    natural_history                 TEXT,
    expected_benefit                TEXT,
    material_risks_considered       TEXT,
    anaesthetic_implications        TEXT,
    blood_implications              TEXT,
    functional_implications         TEXT,
    fertility_implications          TEXT,

    stoma_possibility               BOOLEAN,
    stoma_possibility_notes         TEXT,
    implant_possibility             BOOLEAN,
    implant_possibility_notes       TEXT,

    rehabilitation_expectation      TEXT,
    financial_access_implications   TEXT,
    patient_preference              TEXT,

    -- PROCEED | DO_NOT_PROCEED | DEFER — the vocabulary is closed because "final" means
    -- something was actually decided, not a status this table invents freely.
    final_decision                  VARCHAR(24),
    decided_by                      VARCHAR(255),
    decided_at                      TIMESTAMPTZ,

    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_surgical_decision_episode UNIQUE (surgical_episode_id),
    CONSTRAINT chk_surgical_decision_final CHECK (
        final_decision IS NULL OR final_decision IN ('PROCEED', 'DO_NOT_PROCEED', 'DEFER')),
    -- All three of final_decision/decided_by/decided_at travel together, or none do — a
    -- decision with no author or no timestamp is not a decision anyone can stand behind.
    CONSTRAINT chk_surgical_decision_pair CHECK (
        (final_decision IS NULL AND decided_by IS NULL AND decided_at IS NULL)
        OR (final_decision IS NOT NULL AND decided_by IS NOT NULL AND decided_at IS NOT NULL))
);

CREATE INDEX idx_surgical_decision_tenant ON surgery.surgical_decision (tenant_id);
