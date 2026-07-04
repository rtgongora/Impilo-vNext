-- rtc-gateway-service V003 — recording lifecycle (LiveKit room-composite egress)
-- One row per egress request; status REQUESTED|ACTIVE|COMPLETE|FAILED|STOPPED.
-- consent_reference is snapshotted per recording (recordings are gated
-- separately from session provisioning, per session-template RecordingRule).

-- Sessions did not persist the consent reference captured at provision;
-- the recording consent gate needs it (template recording.consentRequired).
ALTER TABLE rtc.rtc_sessions
    ADD COLUMN consent_reference VARCHAR(255);

CREATE TABLE rtc.recordings (
    id                 BIGSERIAL    PRIMARY KEY,
    session_id         VARCHAR(128) NOT NULL,
    egress_id          VARCHAR(64)  UNIQUE,
    status             VARCHAR(24)  NOT NULL DEFAULT 'REQUESTED',
    layout             VARCHAR(32),
    storage_bucket     VARCHAR(128),
    storage_key        VARCHAR(512),
    document_object_id VARCHAR(128),
    duration_seconds   INT,
    size_bytes         BIGINT,
    started_by         VARCHAR(128),
    started_by_role    VARCHAR(64),
    consent_reference  VARCHAR(255),
    created_at         TIMESTAMPTZ  DEFAULT now(),
    completed_at       TIMESTAMPTZ
);

CREATE INDEX idx_rtc_recordings_session ON rtc.recordings (session_id);
