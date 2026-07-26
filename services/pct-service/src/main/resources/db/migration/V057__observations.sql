-- Discrete clinical observations — the missing spine under everything else in this pack.
--
-- Closes a third broken vertical. FormExtractionService already extracts observation values out of
-- submitted forms and emits pct.form.observation.extracted, but nothing persists them and nothing
-- consumes the event. A nurse filling in a respiratory rate on an IMNCI form produced an event
-- that reached no table and no subscriber. Growth measurements, immunisations and labour
-- observations each got their own registry in this pack; the ordinary observation — a temperature,
-- a saturation, a palmar pallor — still had nowhere to live.
--
-- WHY A SEPARATE TABLE RATHER THAN COLUMNS ON THE ENCOUNTER. Observations are open-ended and
-- accrete: every form, device and guideline adds new ones. Modelling them as columns means a
-- migration per clinical question and a table nobody can read. Modelling them as rows with a code
-- means the growth engine, the danger-sign engine and the SHR all address the same fact the same
-- way.
--
-- DATA_ABSENT_REASON IS THE POINT OF THIS TABLE, NOT AN AFTERTHOUGHT. A row may exist with no
-- value, carrying the reason instead: NOT_ASSESSED, ASKED_BUT_UNKNOWN, REFUSED, UNABLE_TO_OBTAIN,
-- NOT_PERFORMED. That is the difference between "the child's temperature is normal" and "nobody
-- took the child's temperature", and it is the same distinction the danger-sign and classification
-- engines refuse to blur. A schema that can only store values forces the absence to be encoded as
-- a missing row, and a missing row is indistinguishable from a question never asked.
--
-- CC-5: every observation carries subject_cpid plus the journey/encounter it was taken in, so it
-- resolves to the person's care continuum rather than floating beside it.

CREATE TABLE pct.pct_observations (
    observation_id           UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    -- What was observed. The code is the identity of the fact; the display is for humans and is
    -- never matched on.
    code                     VARCHAR(64) NOT NULL,
    code_system              VARCHAR(128) NOT NULL DEFAULT 'impilo-clinical-observation',
    display                  VARCHAR(255),
    category                 VARCHAR(48) NOT NULL DEFAULT 'EXAM',
    -- VITAL_SIGNS | ANTHROPOMETRY | EXAM | SURVEY | LABORATORY | SOCIAL_HISTORY | THERAPY

    effective_at             TIMESTAMPTZ NOT NULL,
    recorded_by              VARCHAR(128) NOT NULL,
    recorded_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Exactly one value shape is populated, or none of them and a data_absent_reason instead.
    value_quantity           NUMERIC(14,4),
    value_unit               VARCHAR(32),
    value_code               VARCHAR(64),
    value_code_system        VARCHAR(128),
    value_boolean            BOOLEAN,
    value_text               TEXT,

    -- Why there is no value. Never inferred: a caller that supplies neither a value nor a reason
    -- is rejected, because the row would then assert nothing while looking like a record.
    data_absent_reason       VARCHAR(48),
    -- NOT_ASSESSED | ASKED_BUT_UNKNOWN | REFUSED | UNABLE_TO_OBTAIN | NOT_PERFORMED | ERROR

    -- How it was interpreted at the time, against the range used at the time. Both are stored
    -- because a later revision of the reference range must not silently reinterpret history.
    interpretation           VARCHAR(24),   -- NORMAL | LOW | HIGH | CRITICAL_LOW | CRITICAL_HIGH | ABNORMAL
    reference_range_low      NUMERIC(14,4),
    reference_range_high     NUMERIC(14,4),
    reference_range_source   VARCHAR(128),

    -- Provenance. An observation extracted from a form must be traceable back to the answer it
    -- came from, or a correction to the form can never be reconciled with the derived row.
    source                   VARCHAR(24) NOT NULL DEFAULT 'MANUAL', -- MANUAL | FORM | DEVICE | DERIVED | IMPORTED
    derived_from_type        VARCHAR(48),   -- FORM_RESPONSE | GROWTH_MEASUREMENT | LABOUR_OBSERVATION
    derived_from_id          VARCHAR(64),
    device_ref               VARCHAR(128),

    status                   VARCHAR(24) NOT NULL DEFAULT 'FINAL', -- FINAL | PRELIMINARY | AMENDED | ENTERED_IN_ERROR
    notes                    TEXT,

    -- A value must be present, or a reason for its absence must be. Both missing is a row that
    -- claims to be an observation and observes nothing.
    CONSTRAINT chk_pct_observation_has_value_or_reason CHECK (
        value_quantity IS NOT NULL
        OR value_code IS NOT NULL
        OR value_boolean IS NOT NULL
        OR value_text IS NOT NULL
        OR data_absent_reason IS NOT NULL
    ),
    -- A quantity without a unit is not a measurement.
    CONSTRAINT chk_pct_observation_quantity_has_unit CHECK (
        value_quantity IS NULL OR value_unit IS NOT NULL
    )
);

CREATE INDEX idx_pct_observations_subject
    ON pct.pct_observations(tenant_id, subject_cpid, effective_at DESC);
CREATE INDEX idx_pct_observations_subject_code
    ON pct.pct_observations(tenant_id, subject_cpid, code, effective_at DESC);
CREATE INDEX idx_pct_observations_encounter
    ON pct.pct_observations(tenant_id, encounter_id)
    WHERE encounter_id IS NOT NULL;

-- An extracted observation is written once per source answer. Re-submitting the same form must
-- correct the row rather than accumulate duplicates that later disagree with each other.
CREATE UNIQUE INDEX uq_pct_observations_derived
    ON pct.pct_observations(tenant_id, derived_from_type, derived_from_id, code)
    WHERE derived_from_id IS NOT NULL AND status <> 'ENTERED_IN_ERROR';
