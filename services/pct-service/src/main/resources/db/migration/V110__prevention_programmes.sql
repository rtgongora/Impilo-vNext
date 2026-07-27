-- Prevention programmes (PCT) — PrEP and TB preventive therapy as programme enrolments.
--
-- W3 completion. PrEP (HIV prevention for HIV-negative people at ongoing risk) and TPT (TB
-- preventive therapy) are longitudinal courses a person is ON, exactly like HIV care or TB
-- treatment — so they are recorded the same way, as pct_programme_enrolments with their own
-- treatment regimen, rather than forking a new store. This widens two closed vocabularies on the
-- W3 tables to admit them.
--
-- WHY THIS IS A WIDENING, AND WHY THAT IS BROWNFIELD-SAFE. Both statements below DROP a CHECK and
-- re-ADD it with additional allowed values. A widened CHECK cannot be violated by any existing row:
-- every row already satisfied the narrower set, so it satisfies the superset. This is the one
-- ALTER-on-a-live-table shape that carries no legacy-data abort risk (unlike V100, which narrowed a
-- free-text column and aborted on the values already there). The tables are also new this wave
-- (V108/V109) with only this pack's writers.
--
-- CONFIDENTIALITY. HIV_PREVENTION (PrEP) is HIV/SRH content and sits on the confidential lane like
-- HIV_CARE; TB_PREVENTION is not confidential, like TB_TREATMENT. That distinction lives in
-- ProgrammeVocabulary.isConfidential, derived from the programme — no second stored flag.

ALTER TABLE pct_programme_enrolments DROP CONSTRAINT pct_programme_enrolments_programme_check;
ALTER TABLE pct_programme_enrolments ADD CONSTRAINT pct_programme_enrolments_programme_check
    CHECK (programme IN (
        'HIV_CARE',
        'TB_TREATMENT',
        'HIV_PREVENTION',   -- PrEP: prophylaxis for an HIV-negative person at ongoing risk
        'TB_PREVENTION'     -- TPT: preventive therapy after active TB has been excluded
    ));

-- The regimen a prevention enrolment carries has no ART line or TB treatment phase — its stage is
-- PREVENTIVE. Widen the stage vocabulary to admit it; the service still refuses a stage that does
-- not belong to the enrolment's programme (an ART line on a PrEP enrolment).
ALTER TABLE pct_treatment_regimens DROP CONSTRAINT pct_treatment_regimens_stage_check;
ALTER TABLE pct_treatment_regimens ADD CONSTRAINT pct_treatment_regimens_stage_check
    CHECK (regimen_stage IN (
        'FIRST_LINE',
        'SECOND_LINE',
        'THIRD_LINE',
        'INTENSIVE_PHASE',
        'CONTINUATION_PHASE',
        'PREVENTIVE'
    ));

COMMENT ON CONSTRAINT pct_programme_enrolments_programme_check ON pct_programme_enrolments IS
    'Closed programme vocabulary: HIV_CARE, TB_TREATMENT (treatment) and HIV_PREVENTION (PrEP), '
    'TB_PREVENTION (TPT). Kept in lockstep with ProgrammeVocabulary.PROGRAMMES by ProgrammeVocabularyTest.';
