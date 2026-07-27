-- Programme treatment regimen (PCT) — the ART or TB regimen a person is on within an enrolment.
--
-- A regimen is not a medicine list and not a prescription. It is the named, governed drug
-- combination the programme is treating with (TDF/3TC/DTG; 2HRZE then 4HR) at a stage of treatment
-- (first/second/third line for ART; intensive/continuation phase for TB). Dispensing and the live
-- medicine list stay OROS's; what belongs here is the treatment-course fact the programme reports
-- against and the reason it changed.
--
-- A REGIMEN CHANGE IS A NEW ROW, NEVER AN OVERWRITE. Switching a patient from first to second line
-- after treatment failure, or moving from the intensive to the continuation phase, is exactly the
-- history a programme must keep: the sequence of regimens and why each ended is the treatment
-- narrative. Overwriting the current regimen would erase it. So the current regimen is the one row
-- with no ended_on, every past regimen is a closed row carrying why it ended, and there is at most
-- one open regimen per enrolment (a hard partial-unique constraint below).
--
-- The regimen_stage vocabulary is deliberately a union of ART lines and TB phases. One column
-- serves both because a regimen is at one stage of one programme; the service refuses a stage that
-- does not belong to the enrolment's programme (an ART line on a TB enrolment), so the union never
-- produces a nonsensical pairing at the write.

CREATE TABLE pct_treatment_regimens (
    regimen_id           UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,

    enrolment_id         UUID            NOT NULL
        REFERENCES pct_programme_enrolments(enrolment_id) ON DELETE CASCADE,
    -- Denormalised for direct query and to keep a regimen legible without a join, exactly as
    -- pct_contraceptive_administrations carries the subject alongside its episode FK.
    subject_cpid         VARCHAR(128)    NOT NULL,

    -- The governed regimen code, from zibo's ART/TB regimen value sets (V035). display is the
    -- human-readable expansion carried alongside so a reader is not left holding a bare code.
    regimen_code         VARCHAR(64)     NOT NULL,
    regimen_display      VARCHAR(255),

    -- Which stage of treatment this regimen represents. ART line or TB phase; see the union note
    -- above. NOT NULL: an unstated line or phase is a fact nobody recorded, not a default.
    regimen_stage        VARCHAR(32)     NOT NULL,

    started_on           DATE            NOT NULL,
    ended_on             DATE,

    -- Why this regimen ended — i.e. why the patient was moved off it. Required exactly when ended_on
    -- is set, forbidden otherwise: a regimen that ended must name the reason (it drives the next
    -- regimen and the failure statistics), and a current regimen has not ended so cannot have one.
    change_reason        VARCHAR(48),

    notes                TEXT,

    recorded_by          VARCHAR(255)    NOT NULL,
    recorded_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_treatment_regimens_stage_check CHECK (regimen_stage IN (
        'FIRST_LINE',            -- ART
        'SECOND_LINE',           -- ART
        'THIRD_LINE',            -- ART
        'INTENSIVE_PHASE',       -- TB
        'CONTINUATION_PHASE'     -- TB
    )),
    CONSTRAINT pct_treatment_regimens_change_reason_check CHECK (change_reason IS NULL OR change_reason IN (
        'NEW_START',             -- first regimen of the enrolment ended into another (rare)
        'PHASE_TRANSITION',      -- TB intensive → continuation
        'TREATMENT_FAILURE',     -- switch of ART line, or TB regimen failure
        'TOXICITY',              -- adverse drug reaction
        'DRUG_INTERACTION',
        'SIMPLIFICATION',        -- move to a preferred/simpler regimen
        'STOCK_OUT',             -- forced substitution; recorded, never hidden
        'PATIENT_PREFERENCE',
        'COMPLETED'              -- planned course finished (continuation phase completed)
    )),
    CONSTRAINT pct_treatment_regimens_ended_check CHECK (
        (ended_on IS NOT NULL AND change_reason IS NOT NULL)
        OR (ended_on IS NULL AND change_reason IS NULL)
    ),
    CONSTRAINT pct_treatment_regimens_date_order_check CHECK (
        ended_on IS NULL OR ended_on >= started_on
    )
);

-- At most one current regimen per enrolment. A new regimen requires the prior one to be ended
-- first, which is what keeps the treatment history a clean sequence rather than two overlapping
-- claims about what the patient is taking today.
CREATE UNIQUE INDEX uq_pct_treatment_regimens_current
    ON pct_treatment_regimens (enrolment_id)
    WHERE ended_on IS NULL;

CREATE INDEX idx_pct_treatment_regimens_enrolment
    ON pct_treatment_regimens (tenant_id, enrolment_id);
CREATE INDEX idx_pct_treatment_regimens_subject
    ON pct_treatment_regimens (tenant_id, subject_cpid);

COMMENT ON TABLE pct_treatment_regimens IS
    'The ART or TB regimen a person is on within a programme enrolment, and the reason each regimen '
    'ended. A change is a new row; the current regimen is the one with no ended_on, and there is at '
    'most one. Dispensing stays with OROS; this is the treatment-course fact the programme reports.';
