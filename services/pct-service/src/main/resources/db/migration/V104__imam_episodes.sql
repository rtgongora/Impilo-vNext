-- Integrated management of acute malnutrition (IMAM) — episode registry.
--
-- Closes the last of the broken verticals in the paediatric pack, and the most direct one.
-- The IMNCI classification engine has been deployed and live-proven since Wave 4 and returns
-- three nutrition classifications, each of which tells the clinician to enrol the child in a
-- treatment programme:
--
--   COMPLICATED_SEVERE_ACUTE_MALNUTRITION  -> "Admit for inpatient therapeutic care"
--   UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION -> "Enrol in outpatient therapeutic care with RUTF,
--                                               review weekly"
--   MODERATE_ACUTE_MALNUTRITION            -> "Enrol in supplementary feeding, review in 14 days"
--
-- There was nowhere to enrol them. The engine issued an instruction the system could not carry
-- out: no episode, no programme, no admission, no discharge criteria, no weekly review, no
-- defaulter tracing. PCT owns the care continuum (care-continuum-doctrine CC-5), so the episode
-- belongs here, and the clinical policy that governs it — admission routes, discharge criteria,
-- review cadence, defaulter definition, ration — stays in the clinical knowledge platform where
-- a protocol revision is a reviewable content diff rather than a schema change.
--
-- Two tables, not one. An episode is the treatment; a review is a contact. Folding the reviews
-- into the episode as "latest MUAC" columns would make the one criterion that matters —
-- sustained across two consecutive visits — unevaluable, because the previous measurement would
-- have been overwritten by the one that replaced it.

CREATE TABLE pct.pct_imam_episodes (
    imam_episode_id          UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    programme                VARCHAR(32) NOT NULL,   -- OTP | SFP | INPATIENT_STABILISATION
    status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | CLOSED

    enrolled_at              TIMESTAMPTZ NOT NULL,
    enrolled_by              VARCHAR(128) NOT NULL,
    -- Where the enrolment came from. An episode opened straight off an IMNCI classification
    -- carries the classification code, which is what makes the chart's instruction traceable to
    -- the treatment that followed it.
    enrolment_source         VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
        -- IMNCI_CLASSIFICATION | GROWTH_MONITORING | COMMUNITY_SCREENING | REFERRAL | DIRECT
    source_classification    VARCHAR(64),
    source_ref               VARCHAR(128),

    -- Admission criteria as recorded at enrolment. Stored as measured rather than re-derived on
    -- read: a child admitted on an arm circumference of 11.2 cm was admitted on that number, and
    -- a later revision of the cut-off must not retrospectively make the admission look wrong.
    admission_criterion      VARCHAR(64),            -- the criterion code that admitted the child
    admission_age_days       INTEGER,
    admission_muac_cm        NUMERIC(4,1),
    admission_weight_kg      NUMERIC(6,3),
    admission_height_cm      NUMERIC(5,1),
    admission_weight_for_height_z NUMERIC(5,2),
    admission_oedema         VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED', -- PRESENT|ABSENT|NOT_ASSESSED
    admission_appetite_test  VARCHAR(16),            -- PASSED | FAILED | NOT_DONE
    admission_complications  TEXT,

    -- Which governed pack admitted this child, so an episode can be read back against the rules
    -- that were in force when it was opened.
    content_pack_id          VARCHAR(128),
    content_version          VARCHAR(64),
    approval_status          VARCHAR(32),

    -- The schedule and the defaulter definition in force when this child was enrolled, copied from
    -- the governed pack at admission. Two reasons they live on the episode rather than being read
    -- from content at query time: a child is held to the rules they were admitted under, and the
    -- tracing queue can then be a single indexed query instead of one network call per open
    -- episode — which is the difference between a worklist a clinic opens every morning and one
    -- nobody opens at all.
    review_interval_days     INTEGER NOT NULL DEFAULT 7,
    attendance_grace_days    INTEGER NOT NULL DEFAULT 0,
    trace_after_missed_visits INTEGER NOT NULL DEFAULT 1,
    defaulter_missed_visits  INTEGER NOT NULL DEFAULT 2,
    last_contact_on          DATE,
    next_review_due          DATE,

    -- Outcome. TRANSFERRED_OUT and MEDICAL_TRANSFER are not outcomes of care — the episode
    -- continues elsewhere — and are kept distinct from CURED so a programme's cure rate cannot be
    -- inflated by children who merely left.
    outcome                  VARCHAR(24),
        -- CURED | DEFAULTED | DIED | NON_RESPONDER | TRANSFERRED_OUT | MEDICAL_TRANSFER
    outcome_at               TIMESTAMPTZ,
    outcome_by               VARCHAR(128),
    outcome_note             TEXT,

    -- Discharge criteria as recorded at the moment the episode was closed, not recomputed later.
    discharge_muac_cm        NUMERIC(4,1),
    discharge_weight_kg      NUMERIC(6,3),
    discharge_oedema         VARCHAR(16),
    discharge_criteria_met   BOOLEAN,                -- NULL = could not be evaluated, never "no"
    discharge_criteria_detail TEXT,
    -- A clinician may discharge a child who does not meet the criteria and sometimes must. What
    -- the system refuses to do is record such a discharge as a cure without a stated reason: a
    -- cure certified on criteria that were not met is a false statistic and a false reassurance.
    discharge_override_reason TEXT,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A child in two open episodes has two treatment plans and no evaluable discharge criterion,
-- because each episode sees half the reviews.
CREATE UNIQUE INDEX uq_pct_imam_active_episode
    ON pct.pct_imam_episodes(tenant_id, subject_cpid)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pct_imam_subject
    ON pct.pct_imam_episodes(tenant_id, subject_cpid, enrolled_at DESC);

-- The tracing queue reads this: open episodes whose next review has come and gone.
CREATE INDEX idx_pct_imam_due
    ON pct.pct_imam_episodes(tenant_id, status, next_review_due)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pct_imam_facility
    ON pct.pct_imam_episodes(tenant_id, facility_id, status)
    WHERE facility_id IS NOT NULL;


CREATE TABLE pct.pct_imam_visits (
    imam_visit_id            UUID PRIMARY KEY,
    imam_episode_id          UUID NOT NULL REFERENCES pct.pct_imam_episodes(imam_episode_id),
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    visit_number             INTEGER NOT NULL,
    scheduled_for            DATE,
    -- A missed visit is a row. Absence recorded as the absence of a row is indistinguishable from
    -- a review that has not come round yet, and that difference is the whole of defaulter tracing.
    attended                 BOOLEAN NOT NULL DEFAULT TRUE,
    visit_date               TIMESTAMPTZ,
    recorded_by              VARCHAR(128) NOT NULL,

    muac_cm                  NUMERIC(4,1),
    weight_kg                NUMERIC(6,3),
    height_cm                NUMERIC(5,1),
    weight_for_height_z      NUMERIC(5,2),
    oedema                   VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED',
    appetite_test            VARCHAR(16),
    danger_signs_present     BOOLEAN,                -- NULL = not assessed, which is not "none"

    rutf_product_code        VARCHAR(32),
    rutf_sachets_issued      NUMERIC(6,2),
    rutf_sachets_recommended NUMERIC(6,2),

    clinical_note            TEXT,
    next_review_due          DATE,

    -- Discharge readiness as evaluated at this contact, stamped rather than recomputed on read,
    -- for the same reason growth z-scores are: the interpretation must travel with the record and
    -- must stay reproducible. NULL means the knowledge platform could not be reached and the
    -- assessment was not made — it never means "not ready".
    discharge_eligible       BOOLEAN,
    attendance_status        VARCHAR(24),
    response_status          VARCHAR(24),
    assessment_note          TEXT,
    assessment_content_version VARCHAR(64),

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_pct_imam_visit_number
    ON pct.pct_imam_visits(tenant_id, imam_episode_id, visit_number);

CREATE INDEX idx_pct_imam_visit_episode
    ON pct.pct_imam_visits(tenant_id, imam_episode_id, visit_date);

CREATE INDEX idx_pct_imam_visit_subject
    ON pct.pct_imam_visits(tenant_id, subject_cpid, visit_date DESC);

-- A visit either happened, in which case it has a date and something was measured, or it did not,
-- in which case it must not carry an attendance date that would make the child look seen.
ALTER TABLE pct.pct_imam_visits
    ADD CONSTRAINT ck_pct_imam_visit_attendance
    CHECK ((attended = TRUE AND visit_date IS NOT NULL) OR (attended = FALSE AND visit_date IS NULL));
