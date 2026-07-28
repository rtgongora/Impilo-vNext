-- =============================================================================================
-- V112 — chronic-disease registers
--
-- brief.md §8 names hypertension, diabetes, chronic kidney disease and chronic respiratory disease
-- as conditions managed on a register. This migration makes them programmes rather than a new table,
-- because V108's own header anticipated exactly that:
--
--     "HIV_CARE and TB_TREATMENT today; NCD registers extend the set (V113-V116 in the lease)
--      without a new table, because a programme enrolment is the same shape whatever the disease."
--
-- Consumed here at V112 rather than V113-V116 — one migration, because the four registers differ
-- only by the value in one column, and four migrations that each add one string would be four
-- deploys to say one thing.
--
-- What a register inherits from V108 for free, and why that is the argument for reusing it:
--   * anchor_problem_id NOT NULL REFERENCES pct_problems — a register entry cannot exist without a
--     recorded diagnosis. "One disease, one entry" is structural rather than a rule someone has to
--     remember. A parallel register table would have had to re-earn this.
--   * uq_pct_programme_enrolments_active — one active entry per person per register.
--   * enrolled_on, managing_facility_id, responsible_actor, programme_number, client_offline_id.
--
-- WHAT DOES NOT FIT, STATED RATHER THAN PAPERED OVER
--
-- The status and exit vocabularies were written for a finite treatment course with an outcome.
-- CURED and TREATMENT_COMPLETED are meaningless for hypertension. They are NOT removed: they remain
-- available to TB, and the CHECK offers values rather than demanding a particular one, so an NCD
-- exit can legitimately use TRANSFERRED_OUT, LOST_TO_FOLLOW_UP, DIED or STOPPED_BY_PATIENT. One
-- reason was genuinely missing and is added below.
--
-- The real gap was that a register is read for CONTROL, and there was no vocabulary for control at
-- all. A hypertension register that can only say a person is enrolled answers the administrative
-- question and not the clinical one.
-- =============================================================================================

-- ---------------------------------------------------------------------------------------------
-- 1. The four NCD registers. A widening of a closed set: no existing row can violate a superset,
--    so this is brownfield-safe, following the V110 pattern.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE pct_programme_enrolments DROP CONSTRAINT pct_programme_enrolments_programme_check;
ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_programme_check
    CHECK (programme IN (
        'HIV_CARE',
        'TB_TREATMENT',
        'HIV_PREVENTION',           -- PrEP
        'TB_PREVENTION',            -- TPT
        -- Chronic-disease registers (brief §8). Lifelong care, not a course with an end.
        'HYPERTENSION',
        'DIABETES',
        'CHRONIC_KIDNEY_DISEASE',
        'CHRONIC_RESPIRATORY'       -- asthma and COPD; one register because the follow-up is shared
    ));

COMMENT ON CONSTRAINT pct_programme_enrolments_programme_check ON pct_programme_enrolments IS
    'Closed programme vocabulary: HIV_CARE, TB_TREATMENT (treatment); HIV_PREVENTION (PrEP), '
    'TB_PREVENTION (TPT); HYPERTENSION, DIABETES, CHRONIC_KIDNEY_DISEASE, CHRONIC_RESPIRATORY '
    '(chronic-disease registers, brief.md §8). Kept in lockstep with ProgrammeVocabulary.PROGRAMMES '
    'by ProgrammeVocabularyTest.';

-- ---------------------------------------------------------------------------------------------
-- 2. The one exit reason a lifelong register genuinely needed.
--
--    A person leaves a hypertension register when the diagnosis turns out to have been wrong — the
--    anchor problem is REFUTED, the raised readings were white-coat or a secondary cause was
--    treated. Before this the only available reasons were a treatment outcome (meaningless here) or
--    STOPPED_BY_PATIENT, which would record the patient as having declined care they never needed.
--    That is not a wording problem: it makes a clinician's error look like a patient's refusal, and
--    it is the patient who carries that in their record.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE pct_programme_enrolments DROP CONSTRAINT pct_programme_enrolments_exit_reason_check;
ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_exit_reason_check
    CHECK (exit_reason IS NULL OR exit_reason IN (
        'TREATMENT_COMPLETED',
        'CURED',
        'TRANSFERRED_OUT',
        'LOST_TO_FOLLOW_UP',
        'DIED',
        'STOPPED_BY_PATIENT',
        'TREATMENT_FAILED',
        'DIAGNOSIS_REFUTED'      -- the condition was not present; never a patient-side reason
    ));

-- ---------------------------------------------------------------------------------------------
-- 3. The control axis — what a chronic register is actually read for.
--
--    Nullable with NO default, because "never assessed" and "assessed and not controlled" are
--    different facts and a default would erase the first. NULL is the honest starting state for
--    every existing row: nobody has assessed control on any of them.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE pct_programme_enrolments
    ADD COLUMN control_status      VARCHAR(32),
    ADD COLUMN control_assessed_on DATE,
    -- The target agreed with THIS person. brief.md §9 requires an individualised target where a
    -- guideline target conflicts with frailty or falls risk, and requires it to be documented AS the
    -- target — otherwise the next clinician reads the standard target and re-escalates treatment the
    -- last one deliberately relaxed.
    ADD COLUMN individualised_target TEXT;

ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_control_status_check
    CHECK (control_status IS NULL OR control_status IN (
        'CONTROLLED',             -- at the agreed target
        'PARTIALLY_CONTROLLED',   -- improving, not yet at target
        'NOT_CONTROLLED',         -- above target on the most recent assessment
        'TARGET_NOT_SET'          -- assessed, but no target has been agreed with the patient yet
    ));

-- A control status with no date is a claim about now built on an assessment of unknown age. Six
-- months after a "CONTROLLED" that nobody dated, the register still reports the patient as
-- controlled, and the register is exactly where somebody looks to decide who needs recalling.
ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_control_dated_check
    CHECK (
        (control_status IS NULL AND control_assessed_on IS NULL)
        OR (control_status IS NOT NULL AND control_assessed_on IS NOT NULL)
    );

ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_control_date_order_check
    CHECK (control_assessed_on IS NULL OR control_assessed_on >= enrolled_on);

COMMENT ON COLUMN pct_programme_enrolments.control_status IS
    'How the condition is running against the agreed target. NULL means never assessed, which is '
    'deliberately distinct from NOT_CONTROLLED — a register read for recall must be able to tell '
    'the patient nobody has reviewed from the patient who is doing badly.';
COMMENT ON COLUMN pct_programme_enrolments.individualised_target IS
    'The target agreed with this person, where it differs from the guideline default (brief.md §9). '
    'Documented here so the next clinician does not re-escalate a target the last one relaxed.';

-- ---------------------------------------------------------------------------------------------
-- 4. The cohort read path.
--
--    Every problem and enrolment query in this estate is subject-scoped: the API asks "what does
--    this patient have", never "who at this facility is on this register". A register IS the second
--    question, and answering it needs an index that leads with the facility rather than the person.
--    This, not the table, was the real cost of building registers.
-- ---------------------------------------------------------------------------------------------
CREATE INDEX idx_pct_programme_enrolments_cohort
    ON pct_programme_enrolments (tenant_id, managing_facility_id, programme, status);

-- Supports "who on this register has not been assessed, or was last assessed long ago" — the recall
-- question. NULLs sort last on this index by default, so the never-assessed are reachable.
CREATE INDEX idx_pct_programme_enrolments_control
    ON pct_programme_enrolments (tenant_id, programme, control_status, control_assessed_on);
