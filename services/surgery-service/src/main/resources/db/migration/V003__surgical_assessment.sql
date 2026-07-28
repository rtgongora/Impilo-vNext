-- =====================================================================================
-- Surgery :: V003 — general surgical assessment (S2, surgical domain pack §5)
--
-- Research first, before this migration: the surgical domain pack's own DAK baseline
-- (docs/clinical/surgical-domain-pack/dak-baseline.md §5) names ~27 assessment elements as
-- absent everywhere. Before adding a column for each, every element was checked against the
-- rest of the estate for an existing canonical home — the same CC-2 discipline S1 already
-- applied to pct_problems, now applied element-by-element rather than assumed once at the
-- table level:
--
--   REFERENCED, not duplicated (a real registry already owns the fact):
--     - allergies                -> pct.pct_allergies (V052)
--     - current medications/anticoagulants -> oros_prescriptions (OROS is explicitly the
--       list; pct_medication_reconciliations V107 only compares, never re-lists)
--     - tobacco/alcohol           -> pct_social_history (V106)
--     - functional status        -> pct_functional_assessments (V106, BARTHEL/LAWTON/KATZ/ECOG)
--     - pregnancy status         -> pct_pregnancy_episodes (V059, one ONGOING per person)
--     - previous surgery         -> pct_past_procedures (V106) — "history... NOT a procedure
--       record", the same distinction this migration draws again below
--     - imaging findings         -> pacs.imaging_study/imaging_report_link + OROS results
--     - pathology findings       -> oros_histopath_report (V014), oros_specimens (V012)
--   This table therefore carries a REVIEWED flag + a short clinical note for each of the
--   above, and a reference id where one is stable enough to carry (functional assessment,
--   pregnancy episode) — never the underlying value itself.
--
--   GENUINELY ABSENT, new content this migration adds fresh (ADR §5a: "structured surgical
--   assessment content" is explicitly something surgery-service MAY own):
--     - presenting problem, symptom timeline, wound-healing history, bleeding/thrombosis
--       history, infection history, nutrition, frailty notes, social support, transport,
--       work/livelihood impact, patient goals, examination findings, differential-diagnosis
--       working notes, surgical risk assessment.
--
--   ONE NAMED DEBT, not silently absorbed: anaesthetic history (a longitudinal "has this
--   patient had an anaesthetic complication before" fact) has no home anywhere — the closest
--   candidate, pct_past_procedures, has no anaesthesia-specific category today. Recorded here
--   as free text (anaesthetic_history_notes) rather than invented as a new PCT category
--   unilaterally — pct-service's own schema is not this migration's to extend, the same
--   restraint S1 already applied to the pct_care_plans attribution gap.
--
-- ONE ROW PER surgical_episode, updated over time as more information comes in — the same
-- mutable-conversation-record idiom mvumo's V300 informed-consent-content migration uses,
-- not versioned rows: an assessment is refined, not replaced.
-- =====================================================================================

CREATE TABLE surgery.surgical_assessment (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    surgical_episode_id             UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    -- ── New content (genuinely absent elsewhere) ──────────────────────────────────────
    presenting_problem              TEXT,
    symptom_timeline                TEXT,
    wound_healing_history           TEXT,
    bleeding_thrombosis_history     TEXT,
    infection_history               TEXT,
    anaesthetic_history_notes       TEXT,  -- named debt: no PCT category exists yet, see header
    nutrition_assessment            TEXT,
    frailty_notes                   TEXT,
    social_support_notes            TEXT,
    transport_plan                  TEXT,
    work_livelihood_impact          TEXT,
    patient_goals                   TEXT,
    examination_findings            TEXT,
    differential_diagnosis_notes    TEXT,
    surgical_risk_assessment        TEXT,

    -- ── Referenced, not duplicated: a reviewed flag + a short note, never the value itself ──
    previous_surgery_reviewed       BOOLEAN NOT NULL DEFAULT FALSE,
    previous_surgery_notes          TEXT,
    allergies_reviewed              BOOLEAN NOT NULL DEFAULT FALSE,
    allergy_review_notes            TEXT,
    medications_reviewed            BOOLEAN NOT NULL DEFAULT FALSE,
    medication_reconciliation_notes TEXT,
    functional_status_reviewed      BOOLEAN NOT NULL DEFAULT FALSE,
    functional_assessment_ref       UUID,   -- pct_functional_assessments.id, resolved at read time
    pregnancy_status_confirmed      BOOLEAN NOT NULL DEFAULT FALSE,
    pregnancy_episode_ref           UUID,   -- pct_pregnancy_episodes.id, only when confirmed ongoing
    imaging_reviewed                BOOLEAN NOT NULL DEFAULT FALSE,
    imaging_ref                     VARCHAR(255),  -- pacs imaging_study id or OROS result id
    pathology_reviewed              BOOLEAN NOT NULL DEFAULT FALSE,
    pathology_ref                   VARCHAR(255),  -- oros_histopath_report id

    assessed_by                     VARCHAR(255),
    assessed_at                     TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_surgical_assessment_episode UNIQUE (surgical_episode_id),
    -- A reference id with the reviewed flag still false is a contradiction: either the
    -- reviewer never actually looked (flag stays false, ref stays null), or they did and the
    -- flag should say so. Mirrors mvumo's withdrawal-pair CHECK idiom (V300).
    CONSTRAINT chk_surgical_assessment_functional_ref CHECK (
        functional_assessment_ref IS NULL OR functional_status_reviewed = TRUE),
    CONSTRAINT chk_surgical_assessment_pregnancy_ref CHECK (
        pregnancy_episode_ref IS NULL OR pregnancy_status_confirmed = TRUE),
    CONSTRAINT chk_surgical_assessment_imaging_ref CHECK (
        imaging_ref IS NULL OR imaging_reviewed = TRUE),
    CONSTRAINT chk_surgical_assessment_pathology_ref CHECK (
        pathology_ref IS NULL OR pathology_reviewed = TRUE)
);

CREATE INDEX idx_surgical_assessment_tenant ON surgery.surgical_assessment (tenant_id);
