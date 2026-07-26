-- =====================================================================================
-- tuso :: V042 — EmONC signal-function readiness (observed, expiring, never inferred)
--
-- WHY THIS IS NOT A CAPABILITY.
--
-- `facility_capability` answers "what service line does this facility offer" and, in this estate,
-- was produced from TEMPLATES: every facility of a tier received the same set, which is why the
-- live counts cluster at 1757 / 904 / 199. `SURGERY` on 199 facilities means "199 facilities are of
-- a type our template says has surgery" — not that a theatre is staffed, equipped and open. Asked
-- whether a facility can perform a caesarean, that data can only mislead.
--
-- WHO/UNFPA/UNICEF signal functions are a different kind of fact. A facility "provides" one if it
-- has ACTUALLY PERFORMED it at least once in the recent recall period — the standard is three
-- months. So readiness is an OBSERVATION with a date, an assessor and an expiry, not an attribute.
-- That difference is the entire reason for this migration:
--
--     capability  = what we were told the facility is for      (template, no expiry)
--     readiness   = what this facility actually did, recently  (observed, decays)
--
-- WHY THIS IS NOT `facility_readiness` EITHER. That table is digital/infrastructure readiness —
-- connectivity, power, devices, EHR — one row per facility with no per-function granularity and no
-- decay. Extending it would merge two unrelated notions of "ready" under one word.
--
-- THE INVARIANT THAT MATTERS CLINICALLY: absence of evidence is not evidence of absence.
-- A facility with no assessment is UNKNOWN, never "not capable". No row is materialised for an
-- unassessed function, and the read model reports UNKNOWN for it — because telling a midwife
-- "this facility cannot do a caesarean" when nobody has ever looked is a different and worse error
-- than telling her "we do not know".
-- =====================================================================================

-- ---- The catalogue: the WHO signal functions, and nothing else ------------------------

CREATE TABLE IF NOT EXISTS tuso.emonc_signal_function (
    code            VARCHAR(48) PRIMARY KEY,
    tier            VARCHAR(16) NOT NULL,          -- BASIC (BEmONC) | COMPREHENSIVE (adds to BEmONC)
    ordinal         INT         NOT NULL,
    display_name    VARCHAR(160) NOT NULL,
    who_definition  TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_emonc_tier CHECK (tier IN ('BASIC', 'COMPREHENSIVE'))
);

-- The canonical nine. BEmONC is the first seven; CEmONC is all nine. This list is closed — a tenth
-- row would mean the platform had invented a signal function, so additions belong in a migration
-- with a citation, not in application code.
INSERT INTO tuso.emonc_signal_function (code, tier, ordinal, display_name, who_definition) VALUES
  ('PARENTERAL_ANTIBIOTICS',   'BASIC', 1, 'Parenteral antibiotics',
   'Administer parenteral antibiotics for maternal or newborn infection.'),
  ('PARENTERAL_UTEROTONICS',   'BASIC', 2, 'Parenteral uterotonics',
   'Administer parenteral uterotonic drugs (e.g. oxytocin) for postpartum haemorrhage.'),
  ('PARENTERAL_ANTICONVULSANTS','BASIC', 3, 'Parenteral anticonvulsants',
   'Administer parenteral anticonvulsants (magnesium sulfate) for pre-eclampsia and eclampsia.'),
  ('MANUAL_REMOVAL_PLACENTA',  'BASIC', 4, 'Manual removal of the placenta',
   'Perform manual removal of a retained placenta.'),
  ('REMOVAL_RETAINED_PRODUCTS','BASIC', 5, 'Removal of retained products',
   'Remove retained products of conception (e.g. manual vacuum aspiration).'),
  ('ASSISTED_VAGINAL_DELIVERY','BASIC', 6, 'Assisted vaginal delivery',
   'Perform assisted vaginal delivery (vacuum extraction or forceps).'),
  ('NEONATAL_RESUSCITATION',   'BASIC', 7, 'Newborn resuscitation',
   'Perform newborn resuscitation with bag and mask.'),
  ('CAESAREAN_SECTION',        'COMPREHENSIVE', 8, 'Caesarean section',
   'Perform caesarean delivery, which requires surgical and anaesthetic capability.'),
  ('BLOOD_TRANSFUSION',        'COMPREHENSIVE', 9, 'Blood transfusion',
   'Perform blood transfusion, which requires a safe and available blood supply.')
ON CONFLICT (code) DO NOTHING;

-- ---- The observation event -------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tuso.facility_readiness_assessment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),

    -- How the facility was looked at. A self-report and a supervisory visit are both legitimate and
    -- are NOT interchangeable, so the method travels with the finding all the way to the reader.
    method              VARCHAR(32) NOT NULL,
    assessed_at         TIMESTAMPTZ NOT NULL,
    assessed_by         VARCHAR(255) NOT NULL,
    assessor_role       VARCHAR(64),

    -- The recall window this assessment asked about, in months. Recorded rather than assumed, so a
    -- survey that used a different window stays interpretable years later.
    recall_window_months INT NOT NULL DEFAULT 3,

    source_reference    VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_assessment_method CHECK (method IN
        ('SELF_REPORTED', 'SUPERVISORY_VISIT', 'INSPECTION', 'FACILITY_SURVEY', 'REGULATOR_RETURN')),
    CONSTRAINT chk_recall_window CHECK (recall_window_months BETWEEN 1 AND 24)
);

CREATE INDEX IF NOT EXISTS idx_readiness_assessment_facility
    ON tuso.facility_readiness_assessment (tenant_id, facility_id, assessed_at DESC);

-- ---- The findings: append-only, one row per function per assessment ---------------------
--
-- Append-only on purpose. A "current status" column would eventually disagree with the evidence
-- behind it, and the disagreement would be invisible. The current view is DERIVED from the latest
-- assessment, so evidence and verdict cannot drift apart.

CREATE TABLE IF NOT EXISTS tuso.facility_signal_function_observation (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    assessment_id       UUID NOT NULL REFERENCES tuso.facility_readiness_assessment(id),
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    signal_function     VARCHAR(48) NOT NULL REFERENCES tuso.emonc_signal_function(code),

    -- PERFORMED     — carried out at least once within the recall window (the WHO test).
    -- NOT_PERFORMED — assessed, and not carried out in the window.
    -- NOT_ASSESSED  — this assessment did not cover this function. Not the same as NOT_PERFORMED,
    --                 and must never collapse into it.
    finding             VARCHAR(24) NOT NULL,

    -- Why not, when NOT_PERFORMED. WHO readiness work consistently finds the barrier matters more
    -- than the fact: "no trained staff" is a different remedy from "no magnesium sulfate".
    barrier             VARCHAR(48),
    last_performed_on   DATE,
    evidence_reference  VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_observation_finding CHECK (finding IN
        ('PERFORMED', 'NOT_PERFORMED', 'NOT_ASSESSED')),
    CONSTRAINT chk_observation_barrier CHECK (barrier IS NULL OR barrier IN
        ('NO_TRAINED_STAFF', 'NO_EQUIPMENT', 'NO_SUPPLIES', 'NO_MEDICINES',
         'NO_BLOOD_SUPPLY', 'REFERRED_ELSEWHERE', 'POLICY_RESTRICTION', 'OTHER')),
    -- A barrier only makes sense against a negative finding.
    CONSTRAINT chk_barrier_only_when_negative CHECK (barrier IS NULL OR finding = 'NOT_PERFORMED'),
    -- A performance date only makes sense against a positive one.
    CONSTRAINT chk_performed_date_only_when_positive
        CHECK (last_performed_on IS NULL OR finding = 'PERFORMED'),
    CONSTRAINT uq_observation_per_assessment UNIQUE (assessment_id, signal_function)
);

CREATE INDEX IF NOT EXISTS idx_signal_observation_facility
    ON tuso.facility_signal_function_observation (tenant_id, facility_id, signal_function);

-- ---- Supporting indexes for the derived read -------------------------------------------

CREATE INDEX IF NOT EXISTS idx_signal_observation_assessment
    ON tuso.facility_signal_function_observation (assessment_id);

-- NOTE deliberately absent from this migration:
--   * no `facility_emonc_classification` table. BEmONC/CEmONC status is COMPUTED from the nine
--     findings and their age. Storing it would create a claim that outlives its evidence — exactly
--     what happens when a facility is labelled CEmONC in a system and the anaesthetist left in
--     March.
--   * no backfill, and no default rows. 7,285 facilities x 9 functions of UNKNOWN would be 65,565
--     rows asserting nothing. An unassessed function has no row and reads as UNKNOWN.
