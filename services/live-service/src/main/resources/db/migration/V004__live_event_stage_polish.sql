-- =============================================
-- Service : live-service (Impilo Live)
-- Schema  : live
-- Flyway  : V004__live_event_stage_polish.sql
-- Purpose : LIVE_EVENT polish (Wave 6):
--           1. Audience→stage promotion requests (server-side role escalation
--              truth: a SPEAKER token is only minted against an APPROVED row
--              or an explicit role assignment — never client-asserted).
--           2. Announcements ride the existing moderated-chat vocabulary via a
--              `kind` discriminator (no parallel announcements table).
--           3. Backstage linkage: the event session remembers its backstage
--              child room (a separate rtc session, parent-linked in rtc).
-- =============================================

CREATE TABLE live.live_event_stage_requests (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    participant_type    VARCHAR(32)     NOT NULL DEFAULT 'CITIZEN',
    -- REQUESTED -> APPROVED | DENIED; APPROVED -> DEMOTED
    status              VARCHAR(16)     NOT NULL DEFAULT 'REQUESTED',
    requested_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    decided_by          VARCHAR(255),
    decided_at          TIMESTAMPTZ
);

CREATE INDEX idx_live_stage_requests_event
    ON live.live_event_stage_requests (event_id, status, requested_at);
CREATE INDEX idx_live_stage_requests_participant
    ON live.live_event_stage_requests (event_id, participant_id);
-- One open (REQUESTED/APPROVED) request per participant per event; a denied or
-- demoted participant may request again with a fresh row.
CREATE UNIQUE INDEX uq_live_stage_request_open
    ON live.live_event_stage_requests (event_id, participant_id)
    WHERE status IN ('REQUESTED', 'APPROVED');

-- Announcements are chat messages of kind ANNOUNCEMENT (reuse the moderated
-- vocabulary + moderation tooling instead of a new table).
ALTER TABLE live.live_event_chat_messages
    ADD COLUMN kind VARCHAR(24) NOT NULL DEFAULT 'CHAT';

CREATE INDEX idx_live_chat_event_kind
    ON live.live_event_chat_messages (event_id, kind, created_at);

-- Backstage child room (rtc session id of the '-backstage' room).
ALTER TABLE live.live_event_sessions
    ADD COLUMN backstage_room_id VARCHAR(255);
