-- Structured history (PCT) — social, family, functional, procedures, advance directives.
--
-- Completing the vertical this lane owes in writing. experience-bff serves
-- /internal/v1/ehr/{social,family,functional,procedures,advance-directives}-history and calls
-- pct-service paths that have never existed; every response currently carries an in_development
-- block naming this wave. These are the tables behind it.
--
-- The column shapes follow the orphaned V32 tables in the BFF's own migrations, which the UI was
-- built against and which have never been created (the BFF has no datasource outside CI). Matching
-- them keeps the experience layer working against the contract it already expects. Four deliberate
-- departures, each because copying V32 faithfully would have copied a defect.
--
--  1. subject_cpid, not patient_id REFERENCES patients(id). The SHR boundary is CPID-only and PCT
--     holds no patient row to reference.
--
--  2. NO DEFAULT ON risk_level. V32 declared `risk_level VARCHAR(32) NOT NULL DEFAULT 'Low'`, which
--     records a clinical judgement nobody made, and of the available defaults 'Low' is the one that
--     stops a reader looking. Same law as pct_problems.severity and diagnostic_certainty: unstated
--     stays NULL.
--
--  3. A functional assessment may record why it could not be scored. V32 made total_score NOT NULL,
--     which forces a number for an assessment that was attempted and could not be completed — a
--     patient too unwell to perform the tasks, an interpreter who did not arrive. The estate already
--     has the right pattern in pct_observations: a value OR a documented reason for its absence,
--     enforced by CHECK. A fabricated zero on a Barthel index reads as total dependence.
--
--  4. No family-member NAME. V32 stored `name VARCHAR(255) NOT NULL` for each relative. That is
--     third-party personal data about someone who is not the patient, has not consented, and has no
--     record here — and it adds almost nothing clinically, because what matters is the relationship,
--     the conditions, the age at onset and the cause of death. A `distinguisher` is kept for the
--     genuine case it was solving (two brothers with different histories), holding 'older brother'
--     or 'maternal aunt' rather than an identity.

-- Social history ---------------------------------------------------------------
CREATE TABLE pct_social_history (
    entry_id      UUID          PRIMARY KEY,
    tenant_id     UUID          NOT NULL,
    subject_cpid  VARCHAR(128)  NOT NULL,
    category      VARCHAR(64)   NOT NULL,   -- TOBACCO | ALCOHOL | SUBSTANCE | OCCUPATION | HOUSING …
    status        VARCHAR(512)  NOT NULL,   -- the recorded finding, e.g. 'Never smoked', 'Ex-smoker'
    detail        TEXT,
    risk_level    VARCHAR(32),              -- NULL = not assessed. Never defaults.
    recorded_by   VARCHAR(255)  NOT NULL,
    last_updated  DATE          NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_social_history_risk_check CHECK (risk_level IS NULL OR risk_level IN (
        'LOW', 'MODERATE', 'HIGH'))
);
CREATE INDEX idx_pct_social_history_subject ON pct_social_history (tenant_id, subject_cpid);
CREATE UNIQUE INDEX uq_pct_social_history_category
    ON pct_social_history (tenant_id, subject_cpid, category);

COMMENT ON COLUMN pct_social_history.risk_level IS
    'NULL means not assessed. Never defaulted — "low" is the one value that stops a reader looking.';

-- Family history ---------------------------------------------------------------
CREATE TABLE pct_family_history_members (
    member_id      UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    relationship   VARCHAR(64)   NOT NULL,  -- MOTHER | FATHER | SIBLING | …
    -- Distinguishes relatives of the same relationship without recording their identity.
    distinguisher  VARCHAR(128),
    age            INTEGER,
    deceased       BOOLEAN,                 -- NULL = unknown, not alive
    deceased_age   INTEGER,
    cause_of_death VARCHAR(512),
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_family_member_age_check CHECK (age IS NULL OR age BETWEEN 0 AND 130),
    CONSTRAINT pct_family_member_deceased_age_check
        CHECK (deceased_age IS NULL OR deceased_age BETWEEN 0 AND 130),
    -- A deceased age or cause on a relative not recorded as deceased is a contradiction the record
    -- should not be able to hold.
    CONSTRAINT pct_family_member_deceased_consistency CHECK (
        (deceased IS TRUE) OR (deceased_age IS NULL AND cause_of_death IS NULL))
);
CREATE INDEX idx_pct_family_members_subject ON pct_family_history_members (tenant_id, subject_cpid);

COMMENT ON COLUMN pct_family_history_members.distinguisher IS
    'Distinguishes two relatives of the same relationship — "older brother", "maternal aunt". '
    'Deliberately not a name: a relative is a third party who has not consented and has no record '
    'here, and the relationship is what carries the clinical meaning.';
COMMENT ON COLUMN pct_family_history_members.deceased IS
    'NULL means unknown. Not the same as alive — a family history nobody completed must not read '
    'as a family of living relatives.';

CREATE TABLE pct_family_history_conditions (
    condition_id    UUID          PRIMARY KEY,
    member_id       UUID          NOT NULL REFERENCES pct_family_history_members(member_id) ON DELETE CASCADE,
    tenant_id       UUID          NOT NULL,
    condition_label VARCHAR(512)  NOT NULL,
    code            VARCHAR(64),
    code_system     VARCHAR(64),
    onset_age       INTEGER,
    status          VARCHAR(32),
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_family_condition_onset_check CHECK (onset_age IS NULL OR onset_age BETWEEN 0 AND 130)
);
CREATE INDEX idx_pct_family_conditions_member ON pct_family_history_conditions (member_id);

-- Functional assessments -------------------------------------------------------
CREATE TABLE pct_functional_assessments (
    assessment_id    UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    subject_cpid     VARCHAR(128)  NOT NULL,
    assessment_type  VARCHAR(48)   NOT NULL,  -- BARTHEL | LAWTON | KATZ | ECOG | …
    assessment_date  DATE          NOT NULL,
    assessor         VARCHAR(255),
    total_score      INTEGER,
    max_score        INTEGER,
    -- Why there is no score. Mirrors pct_observations.data_absent_reason.
    score_absent_reason VARCHAR(64),
    interpretation   TEXT,
    activities       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    recorded_by      VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- A score or a reason, never neither and never both. Without this an incomplete assessment
    -- must invent a number, and a fabricated zero on a Barthel index reads as total dependence.
    CONSTRAINT pct_functional_score_or_reason CHECK (
        (total_score IS NOT NULL AND score_absent_reason IS NULL)
        OR (total_score IS NULL AND score_absent_reason IS NOT NULL)),
    CONSTRAINT pct_functional_score_range CHECK (
        total_score IS NULL OR max_score IS NULL OR (total_score >= 0 AND total_score <= max_score))
);
CREATE INDEX idx_pct_functional_subject
    ON pct_functional_assessments (tenant_id, subject_cpid, assessment_date DESC);

-- Past procedures --------------------------------------------------------------
-- The patient's procedural history as told or as recorded elsewhere. NOT a procedure record:
-- procedures-service owns execution and inpatient owns the theatre episode. This is history.
CREATE TABLE pct_past_procedures (
    procedure_id    UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    subject_cpid    VARCHAR(128)  NOT NULL,
    name            VARCHAR(512)  NOT NULL,
    code            VARCHAR(64),
    code_system     VARCHAR(64),
    procedure_type  VARCHAR(128),
    procedure_date  DATE,                    -- nullable: "some years ago" is a real answer
    date_precision  VARCHAR(16),             -- EXACT | MONTH | YEAR | APPROXIMATE
    performer       VARCHAR(255),
    facility        VARCHAR(255),
    status          VARCHAR(48)   NOT NULL DEFAULT 'COMPLETED',
    -- Links to the executed record when this history entry corresponds to one this estate holds.
    source_episode_ref VARCHAR(128),
    notes           TEXT,
    recorded_by     VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_past_procedure_status_check CHECK (status IN (
        'COMPLETED', 'ABANDONED', 'REPORTED_BY_PATIENT')),
    CONSTRAINT pct_past_procedure_precision_check CHECK (date_precision IS NULL OR date_precision IN (
        'EXACT', 'MONTH', 'YEAR', 'APPROXIMATE'))
);
CREATE INDEX idx_pct_past_procedures_subject
    ON pct_past_procedures (tenant_id, subject_cpid, procedure_date DESC NULLS LAST);

COMMENT ON COLUMN pct_past_procedures.date_precision IS
    'How exact procedure_date is. A patient saying "about ten years ago" produces a date and an '
    'APPROXIMATE precision; storing it as exact would let a later reader compute intervals from it.';
COMMENT ON TABLE pct_past_procedures IS
    'Procedural history — what has been done to this patient before, however it was learned. Not a '
    'procedure record: procedures-service owns execution and inpatient owns the theatre episode.';

-- Advance directives -----------------------------------------------------------
CREATE TABLE pct_advance_directives (
    directive_id    UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    subject_cpid    VARCHAR(128)  NOT NULL,
    directive_type  VARCHAR(64)   NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    summary         TEXT,
    -- The document this rests on, in pct_clinical_documents (V102).
    document_id     UUID          REFERENCES pct_clinical_documents(document_id),
    effective_from  DATE,
    reviewed_on     DATE,
    -- A directive with no recorded review is not a stale directive; it is one nobody has reviewed,
    -- and those are different things to a clinician deciding whether to rely on it.
    recorded_by     VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_advance_directive_status_check CHECK (status IN (
        'ACTIVE', 'SUPERSEDED', 'REVOKED', 'EXPIRED'))
);
CREATE INDEX idx_pct_advance_directives_subject
    ON pct_advance_directives (tenant_id, subject_cpid);

COMMENT ON TABLE pct_advance_directives IS
    'Advance care directives. "No advance directive" is among the most consequential affirmative '
    'claims this system can make, so the absence of a row here must never be rendered as one by a '
    'caller that failed to read.';
