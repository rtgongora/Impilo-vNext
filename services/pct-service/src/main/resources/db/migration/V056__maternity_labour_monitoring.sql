-- Labour monitoring, the WHO partograph, and cardiotocography.
--
-- Closes a broken vertical. experience-bff has shipped GET/POST /internal/v1/labour-monitoring,
-- the partograph session endpoints and the CTG streaming endpoints for some time, all of which
-- proxy to pct-service /v1/maternity/**, which never existed. The GET swallowed the failure and
-- returned an empty list, so a labour record appeared to save and vanished; the CTG stream was
-- served from an in-process map that emptied on every pod restart. Nothing was ever persisted:
-- the BFF carries Flyway scripts V34-V36 for these tables, but the BFF has no datasource, so
-- those scripts have never run and the tables exist in no database. There is therefore no data
-- to migrate — only a contract to make real.
--
-- ONE OBSERVATION TABLE, NOT TWO. The orphan BFF schemas defined labour_monitoring_entries and
-- maternity_partograph_points with identical clinical columns. Reproducing both would create two
-- systems of record for the same fact, and a partograph drawn from one while the labour record
-- listed the other would disagree about the same woman. A partograph point IS a labour
-- observation; the only difference is whether a partograph session was open when it was taken.
-- So there is one table, and partograph_session_id is nullable.
--
-- CC-5 anchoring: every row carries subject_cpid and the journey/encounter it was observed in,
-- so a labour record resolves to the mother's care continuum rather than floating beside it.

CREATE TABLE pct.pct_labour_observations (
    observation_id           UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    -- Null when the observation was taken outside an open partograph session (triage, an
    -- admission check, the latent phase). Set once a session is open. The clinical content is
    -- identical either way, which is precisely why there is only one table.
    partograph_session_id    UUID,

    observed_at              TIMESTAMPTZ NOT NULL,
    recorded_by              VARCHAR(128) NOT NULL,
    phase                    VARCHAR(32) NOT NULL DEFAULT 'ACTIVE_LABOUR', -- LATENT | ACTIVE_LABOUR | SECOND_STAGE | THIRD_STAGE

    -- Fetal condition.
    fetal_heart_rate_bpm         INTEGER,
    liquor                       VARCHAR(24),  -- INTACT | CLEAR | MECONIUM_* | BLOOD_STAINED | ABSENT
    moulding                     VARCHAR(24),  -- NONE | PLUS_1 | PLUS_2 | PLUS_3
    caput                        VARCHAR(24),

    -- Progress of labour.
    cervical_dilation_cm         NUMERIC(4,1),
    fetal_descent_fifths         INTEGER,
    contraction_frequency_10min  INTEGER,
    contraction_duration_sec     INTEGER,

    -- Maternal condition.
    maternal_pulse_bpm           INTEGER,
    systolic_bp                  INTEGER,
    diastolic_bp                 INTEGER,
    temperature_c                NUMERIC(4,1),
    urine_volume_ml              INTEGER,
    urine_protein                VARCHAR(24),
    urine_acetone                VARCHAR(24),
    maternal_condition           TEXT,

    -- Interventions in progress at the time of the observation.
    oxytocin_rate_miu_min        NUMERIC(6,2),

    notes                        TEXT,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_labour_obs_subject
    ON pct.pct_labour_observations(tenant_id, subject_cpid, observed_at DESC);
CREATE INDEX idx_pct_labour_obs_session
    ON pct.pct_labour_observations(partograph_session_id, observed_at ASC)
    WHERE partograph_session_id IS NOT NULL;
CREATE INDEX idx_pct_labour_obs_encounter
    ON pct.pct_labour_observations(tenant_id, encounter_id)
    WHERE encounter_id IS NOT NULL;


-- The partograph session: one labour, one graph.
--
-- alert_reference_at / alert_reference_dilation_cm pin where the alert line starts. The WHO
-- partograph plots an alert line rising 1 cm/hour from the point active labour is recognised,
-- with the action line four hours to its right. Those two anchors are stored rather than
-- recomputed on read, because the line must not move when a historical observation is corrected
-- or when the guideline's active-phase threshold is next revised.
CREATE TABLE pct.pct_partograph_sessions (
    session_id               UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | CLOSED
    labour_phase             VARCHAR(32) NOT NULL DEFAULT 'ACTIVE_LABOUR',

    started_at               TIMESTAMPTZ NOT NULL,
    started_by               VARCHAR(128) NOT NULL,
    closed_at                TIMESTAMPTZ,
    closed_by                VARCHAR(128),
    outcome                  VARCHAR(64),
    summary_notes            TEXT,

    alert_reference_at              TIMESTAMPTZ,
    alert_reference_dilation_cm     NUMERIC(4,1),
    partograph_content_version      VARCHAR(64),

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One open partograph per woman. A second concurrent session would split the graph in two and
-- neither half would show the true progress of the labour.
CREATE UNIQUE INDEX uq_pct_partograph_active
    ON pct.pct_partograph_sessions(tenant_id, subject_cpid)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_pct_partograph_subject
    ON pct.pct_partograph_sessions(tenant_id, subject_cpid, started_at DESC);

ALTER TABLE pct.pct_labour_observations
    ADD CONSTRAINT fk_labour_obs_partograph
    FOREIGN KEY (partograph_session_id) REFERENCES pct.pct_partograph_sessions(session_id);


-- Cardiotocography.
CREATE TABLE pct.pct_ctg_sessions (
    session_id               UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,
    journey_id               VARCHAR(64),
    encounter_id             VARCHAR(64),
    facility_id              UUID,

    status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | CLOSED
    monitoring_mode          VARCHAR(32) NOT NULL DEFAULT 'EXTERNAL_CTG',
    device_id                VARCHAR(128),

    started_at               TIMESTAMPTZ NOT NULL,
    started_by               VARCHAR(128) NOT NULL,
    closed_at                TIMESTAMPTZ,
    closed_by                VARCHAR(128),

    baseline_fhr_bpm         INTEGER,
    baseline_maternal_hr_bpm INTEGER,
    summary_notes            TEXT,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_pct_ctg_active
    ON pct.pct_ctg_sessions(tenant_id, subject_cpid)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_pct_ctg_subject
    ON pct.pct_ctg_sessions(tenant_id, subject_cpid, started_at DESC);

-- Trace chunks. The samples are stored as an array under a declared sample rate and count
-- rather than one row per sample: a 30-minute external trace at 4 Hz is 7,200 points per
-- channel, and the clinical unit of review is the segment, not the sample.
CREATE TABLE pct.pct_ctg_chunks (
    chunk_id                 UUID PRIMARY KEY,
    session_id               UUID NOT NULL REFERENCES pct.pct_ctg_sessions(session_id),
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,

    channel                  VARCHAR(24) NOT NULL,  -- FHR | UTERINE_ACTIVITY | MATERNAL_HR
    started_at               TIMESTAMPTZ NOT NULL,
    ended_at                 TIMESTAMPTZ,
    sample_rate_hz           NUMERIC(8,3) NOT NULL,
    sample_count             INTEGER NOT NULL,
    duration_seconds         NUMERIC(10,3) NOT NULL,
    unit                     VARCHAR(24) NOT NULL DEFAULT 'BPM',
    samples_json             JSONB NOT NULL,

    -- Gaps are recorded, not interpolated. A transducer that slipped for ninety seconds must
    -- read as ninety seconds of absent trace, never as a reassuring flat line.
    missing_sample_count     INTEGER NOT NULL DEFAULT 0,

    captured_by              VARCHAR(128),
    device_id                VARCHAR(128),
    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_ctg_chunks_session
    ON pct.pct_ctg_chunks(session_id, channel, started_at ASC);

CREATE TABLE pct.pct_ctg_annotations (
    annotation_id            UUID PRIMARY KEY,
    session_id               UUID NOT NULL REFERENCES pct.pct_ctg_sessions(session_id),
    tenant_id                UUID NOT NULL,
    subject_cpid             VARCHAR(64) NOT NULL,

    recorded_at              TIMESTAMPTZ NOT NULL,
    category                 VARCHAR(64) NOT NULL,
    channel                  VARCHAR(24),
    sample_offset_sec        NUMERIC(10,3),
    value                    VARCHAR(255),
    severity                 VARCHAR(24),
    notes                    TEXT,
    recorded_by              VARCHAR(128) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_ctg_annotations_session
    ON pct.pct_ctg_annotations(session_id, recorded_at ASC);
