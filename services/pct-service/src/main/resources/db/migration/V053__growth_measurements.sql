-- Paediatric growth measurement registry.
--
-- Closes a broken vertical: experience-bff has shipped GET/POST /internal/v1/growth
-- (with a real WHO 2006 z-score engine) and the UI growth-chart page since the Wave 4
-- slice, but both proxied to pct-service /v1/growth, which never existed. The BFF
-- swallowed the 404 and returned an empty list, so measurements appeared to save and
-- silently vanished. PCT is the clinical encounter system of record, so the registry
-- belongs here and the existing BFF and UI need no rework.
--
-- Z-scores are stamped at write time rather than computed on read, for three reasons:
-- a measurement recorded offline must carry its interpretation with it; a stored score
-- must stay reproducible; and a future revision of the growth standard must not silently
-- reinterpret historical measurements. The standard id and engine version are therefore
-- stored alongside every score.
--
-- Measurement context (posture, equipment, clothing, observer, confidence) is captured
-- because a growth trajectory is only as trustworthy as the conditions it was measured
-- under: a child weighed in a blanket on a different scale explains an apparent gain far
-- more often than real growth does.

CREATE TABLE pct.pct_growth_measurements (
    growth_id                UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    measured_at              TIMESTAMPTZ NOT NULL,
    recorded_by              VARCHAR(128) NOT NULL,

    -- Anthropometry as measured. Length and height are stored separately: which one was
    -- taken determines which WHO curve applies, and conflating them shifts a child by
    -- roughly 0.7 cm, enough to cross a classification boundary.
    weight_kg                NUMERIC(6,3),
    length_cm                NUMERIC(5,1),
    height_cm                NUMERIC(5,1),
    head_circumference_cm    NUMERIC(4,1),
    muac_cm                  NUMERIC(4,1),
    bmi                      NUMERIC(6,3),
    oedema                   VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED', -- PRESENT | ABSENT | NOT_ASSESSED

    -- Measurement conditions.
    measurement_mode         VARCHAR(16),   -- LENGTH | HEIGHT | AUTO
    posture                  VARCHAR(16),   -- RECUMBENT | STANDING
    equipment_ref            VARCHAR(128),
    clothing                 VARCHAR(24),   -- MINIMAL | CLOTHED | NAPPY_ONLY
    observer_cadre           VARCHAR(64),
    measurement_confidence   VARCHAR(16) NOT NULL DEFAULT 'MEASURED', -- MEASURED | ESTIMATED | UNCERTAIN

    -- Age at measurement, resolved once at write time so a later change to the recorded
    -- date of birth cannot retrospectively alter a scored measurement without a trace.
    age_days                 INTEGER,
    corrected_age_days       INTEGER,
    corrected_age_applied    BOOLEAN NOT NULL DEFAULT FALSE,
    gestational_age_weeks    INTEGER,

    -- WHO z-scores as stamped at write, with provenance.
    weight_for_age_z             NUMERIC(5,2),
    length_height_for_age_z      NUMERIC(5,2),
    bmi_for_age_z                NUMERIC(5,2),
    head_circumference_for_age_z NUMERIC(5,2),
    growth_standard          VARCHAR(64),
    growth_engine_version    VARCHAR(32),
    scoring_note             TEXT,          -- why no score could be produced, when applicable

    -- Nutrition conclusion drawn from MUAC, oedema and weight-for-age at this contact.
    nutrition_status         VARCHAR(40),   -- see NutritionStatus; NOT_ASSESSED is not "adequate"
    nutrition_content_version VARCHAR(64),

    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_growth_subject
    ON pct.pct_growth_measurements(tenant_id, subject_cpid, measured_at DESC);
CREATE INDEX idx_pct_growth_encounter
    ON pct.pct_growth_measurements(tenant_id, encounter_id)
    WHERE encounter_id IS NOT NULL;
