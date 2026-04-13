CREATE TABLE IF NOT EXISTS ctg_monitoring_sessions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   VARCHAR(255) NOT NULL,
    patient_id                  UUID NOT NULL,
    encounter_id                UUID,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_by                  VARCHAR(255),
    closed_at                   TIMESTAMPTZ,
    closed_by                   VARCHAR(255),
    device_id                   VARCHAR(255),
    monitoring_mode             VARCHAR(40) NOT NULL DEFAULT 'EXTERNAL_CTG',
    baseline_fhr_bpm            INTEGER,
    baseline_maternal_hr_bpm    INTEGER,
    summary_notes               TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ctg_sessions_patient
    ON ctg_monitoring_sessions (tenant_id, patient_id, status, started_at DESC);

CREATE INDEX idx_ctg_sessions_encounter
    ON ctg_monitoring_sessions (tenant_id, encounter_id, status, started_at DESC);

CREATE TABLE IF NOT EXISTS ctg_trace_chunks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES ctg_monitoring_sessions(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(255) NOT NULL,
    patient_id          UUID NOT NULL,
    encounter_id        UUID,
    channel             VARCHAR(20) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ,
    sample_rate_hz      NUMERIC(8,3) NOT NULL,
    sample_count        INTEGER NOT NULL,
    duration_seconds    NUMERIC(10,3) NOT NULL,
    unit                VARCHAR(20) NOT NULL DEFAULT 'BPM',
    samples_json        JSONB NOT NULL,
    captured_by         VARCHAR(255),
    device_id           VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ctg_chunks_session_channel
    ON ctg_trace_chunks (session_id, channel, started_at ASC);

CREATE INDEX idx_ctg_chunks_patient
    ON ctg_trace_chunks (tenant_id, patient_id, started_at DESC);

CREATE TABLE IF NOT EXISTS ctg_annotations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES ctg_monitoring_sessions(id) ON DELETE CASCADE,
    tenant_id           VARCHAR(255) NOT NULL,
    patient_id          UUID NOT NULL,
    encounter_id        UUID,
    recorded_at         TIMESTAMPTZ NOT NULL,
    category            VARCHAR(60) NOT NULL,
    channel             VARCHAR(20),
    sample_offset_sec   NUMERIC(8,3),
    value               VARCHAR(255),
    severity            VARCHAR(20),
    notes               TEXT,
    recorded_by         VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ctg_annotations_session
    ON ctg_annotations (session_id, recorded_at ASC);

CREATE INDEX idx_ctg_annotations_patient
    ON ctg_annotations (tenant_id, patient_id, recorded_at DESC);
