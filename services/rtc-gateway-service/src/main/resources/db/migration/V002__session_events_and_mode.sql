-- rtc-gateway-service V002 — session-template mode + LiveKit webhook telemetry
-- session_mode / owning_service / owning_ref are nullable: rows provisioned
-- before this migration have no mode and default to TELEMEDICINE in code.

ALTER TABLE rtc.rtc_sessions
    ADD COLUMN session_mode      VARCHAR(32),
    ADD COLUMN owning_service    VARCHAR(32),
    ADD COLUMN owning_ref        VARCHAR(128),
    ADD COLUMN started_at        TIMESTAMPTZ,
    ADD COLUMN ended_at          TIMESTAMPTZ,
    ADD COLUMN peak_participants INT;

-- Webhooks resolve sessions by LiveKit room name.
CREATE INDEX idx_rtc_sessions_room_name ON rtc.rtc_sessions (room_name);

-- Raw LiveKit webhook events (idempotent on the LiveKit event id).
CREATE TABLE rtc.session_events (
    id                   BIGSERIAL    PRIMARY KEY,
    session_id           VARCHAR(128),
    lk_event_id          VARCHAR(64)  UNIQUE,
    event_type           VARCHAR(64)  NOT NULL,
    participant_identity VARCHAR(128),
    room_name            VARCHAR(255),
    payload              JSONB,
    occurred_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_rtc_session_events_session ON rtc.session_events (session_id, occurred_at);

-- Per-participant telemetry derived from participant_joined/participant_left.
CREATE TABLE rtc.participant_stats (
    id                 BIGSERIAL    PRIMARY KEY,
    session_id         VARCHAR(128),
    identity           VARCHAR(128),
    joined_at          TIMESTAMPTZ,
    left_at            TIMESTAMPTZ,
    duration_seconds   INT,
    connection_quality VARCHAR(16),
    disconnect_reason  VARCHAR(64),
    payload            JSONB,
    created_at         TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_rtc_participant_stats_session ON rtc.participant_stats (session_id, identity);
