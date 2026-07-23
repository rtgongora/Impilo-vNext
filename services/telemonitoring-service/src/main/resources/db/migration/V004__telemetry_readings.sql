-- =====================================================================================
-- Telemonitoring — Telemetry readings + single-writer SHR ledger :: V004 (OF-B25)
--
-- NOTE ON NUMBERING: V001 plans, V002 governance, V003 device assignments; V004 is the
-- next free number (verified by ls at authoring time).
--
-- One table carries both the clinical-lane copy of every consumed reading AND its
-- quarantine/SHR-write posture (§14.5 steps 11-14):
--
--  * status VALID / DEGRADED_VALID / QUARANTINED — quarantined readings are RETAINED
--    with a coded reason, never silently dropped (§15.7; journey #62). For every
--    quarantine class the patient attribution columns stay NULL: a wrong-record write
--    is the failure criterion, so an unattributable reading must never carry a
--    patient_cpid/plan_id it could later be projected under.
--  * dedup_key — sha-256 over device_ref|metric|measured_at. UNIQUE: the ingest-side
--    idempotency of journey #64 (replayed bus messages collapse to one row; the DB
--    constraint backstops the pre-flight check under concurrent consumption).
--  * shr_* columns — the single-designated-writer ledger (§14.5 step 14, closes the
--    R72 three-writer sprawl). shr_written_at/shr_ref are set ONLY on gateway outcome
--    SUCCESS (honesty law); failures are counted, coded and retried boundedly.
--  * quality_stamp JSONB — trust/calibration/source posture travelling with the
--    reading into the Observation (§15.7 "the stamp travels"); DEGRADED_VALID readings
--    are stamped never dropped, and OF-B26 gates them out of value-alerting.
-- =====================================================================================

CREATE TABLE telemonitoring.tm_readings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    source_reading_id     VARCHAR(64),                -- iot-ingestion reading_id (provenance, step 10 -> 11)
    device_ref            VARCHAR(64) NOT NULL,       -- iot.device_registry device_id
    assignment_id         UUID,                       -- resolved DeviceAssignmentId (NULL when quarantined unattributed)
    plan_id               UUID,                       -- resolved plan (NULL when quarantined)
    patient_cpid          VARCHAR(64),                -- CPID-only, NULL for every QUARANTINED row (journey #62)
    metric_code           VARCHAR(64) NOT NULL,
    metric_value          NUMERIC(14, 4),             -- NULL only for UNPARSEABLE_VALUE quarantine
    unit                  VARCHAR(32),
    measured_at           TIMESTAMPTZ NOT NULL,       -- original device timestamp, never overwritten (§15.8-3)
    received_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source                VARCHAR(64),                -- ingest source (HTTP/BATCH/...) as emitted by iot-ingestion
    quality_stamp         JSONB,                      -- {trustLevel, calibrationPosture, calibrationReason, source}
    dedup_key             VARCHAR(64) NOT NULL,       -- sha256(device_ref|metric|measured_at) — #64 ingest idempotency
    status                VARCHAR(24) NOT NULL,       -- VALID / DEGRADED_VALID / QUARANTINED
    quarantine_reason     VARCHAR(48),                -- NO_ASSIGNMENT / SUSPENDED_ASSIGNMENT / SUBJECT_MISMATCH / UNPARSEABLE_VALUE
    shr_write_state       VARCHAR(24) NOT NULL DEFAULT 'NOT_ELIGIBLE',
    -- NOT_ELIGIBLE (quarantined / plan not ACTIVE) / PENDING / RETRYING / WRITTEN / EXHAUSTED
    shr_write_attempts    INT NOT NULL DEFAULT 0,     -- bounded retry ledger (honesty law)
    shr_last_write_error  VARCHAR(255),               -- coded gateway outcome of the last failed attempt
    shr_written_at        TIMESTAMPTZ,                -- set ONLY on gateway SUCCESS
    shr_ref               VARCHAR(128),               -- gateway audit ref of the successful forward
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_readings_dedup UNIQUE (dedup_key)
);

CREATE INDEX idx_tm_readings_tenant_plan   ON telemonitoring.tm_readings (tenant_id, plan_id, measured_at DESC);
CREATE INDEX idx_tm_readings_device        ON telemonitoring.tm_readings (device_ref, measured_at DESC);
CREATE INDEX idx_tm_readings_quarantine    ON telemonitoring.tm_readings (tenant_id, status) WHERE status = 'QUARANTINED';
-- Retry-sweep population: only rows still owed to the SHR.
CREATE INDEX idx_tm_readings_write_pending ON telemonitoring.tm_readings (shr_write_state)
    WHERE shr_write_state IN ('PENDING', 'RETRYING');
