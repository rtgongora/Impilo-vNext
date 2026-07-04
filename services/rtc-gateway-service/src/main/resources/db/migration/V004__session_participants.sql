-- rtc-gateway-service V004 — waiting-room lobby participants
-- One row per (session, identity). State machine:
--   WAITING   → participant requested a token but the session template's
--               lobbyBehaviour gates non-roomAdmin roles until admitted
--   ADMITTED  → admitted by a roomAdmin (or auto-admitted roomAdmin role)
--   DENIED    → refused; token requests keep returning DENIED
--   CONNECTED → LiveKit participant_joined observed
--   LEFT      → LiveKit participant_left observed (still admitted; may rejoin)

CREATE TABLE rtc.session_participants (
    id             BIGSERIAL    PRIMARY KEY,
    session_id     VARCHAR(128) NOT NULL,
    identity       VARCHAR(128) NOT NULL,
    display_name   VARCHAR(255),
    role           VARCHAR(64),
    state          VARCHAR(16)  NOT NULL,
    media_profile  VARCHAR(16),
    requested_at   TIMESTAMPTZ  DEFAULT now(),
    admitted_at    TIMESTAMPTZ,
    admitted_by    VARCHAR(128),
    denied_reason  VARCHAR(255),
    UNIQUE (session_id, identity)
);

CREATE INDEX idx_rtc_session_participants_session ON rtc.session_participants (session_id);
