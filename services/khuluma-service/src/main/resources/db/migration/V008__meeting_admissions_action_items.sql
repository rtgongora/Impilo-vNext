-- =============================================================================
-- khuluma-service V008 — Meeting lobby admissions + action items (W5 MEETING)
--
-- A Khuluma meeting is a MEETING conversation composed over an Impilo Live
-- event whose media room is an rtc-gateway MEETING-template session with a
-- KNOCK lobby. The TRANSPORT truth of the lobby lives in rtc-gateway
-- (rtc.session_participants WAITING|ADMITTED|DENIED|CONNECTED|LEFT); Khuluma
-- mirrors the MEETING-domain record of decisions it makes (who knocked, who
-- admitted/denied whom, and the attendance stamps derived from the
-- impilo.rtc.participant.* media-truth events Khuluma already consumes).
--
-- Also from this version the meeting conversation accepts AGENDA|NOTE message
-- content types (khuluma_messages.content_type is an un-CHECKed VARCHAR —
-- vocabulary extension is code-side, no DDL needed).
-- =============================================================================

-- ── Meeting lobby admissions (domain mirror of rtc lobby decisions) ─────────
CREATE TABLE khuluma_meeting_admissions (
    admission_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    conversation_id       UUID            NOT NULL REFERENCES khuluma_conversations (conversation_id),
    actor_id              VARCHAR(255)    NOT NULL,
    actor_type            VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    display_name          VARCHAR(255),
    meeting_role          VARCHAR(32),                                -- HOST|COHOST|PARTICIPANT|OBSERVER (template role at last join)
    status                VARCHAR(32)     NOT NULL DEFAULT 'WAITING', -- WAITING|ADMITTED|DENIED
    rtc_session_id        VARCHAR(255),                               -- rtc-gateway session backing this meeting's media room
    requested_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    decided_by            VARCHAR(255),                               -- host/cohost actor (or ROOM_ADMIN_AUTO for admin self-admits)
    decided_at            TIMESTAMPTZ,
    denied_reason         VARCHAR(255),
    joined_at             TIMESTAMPTZ,                                -- attendance proxy: first impilo.rtc.participant.joined
    left_at               TIMESTAMPTZ,                                -- attendance proxy: last impilo.rtc.participant.left
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_meeting_admission UNIQUE (conversation_id, actor_id)
);

CREATE INDEX idx_khuluma_meetadm_conv    ON khuluma_meeting_admissions (conversation_id, status);
CREATE INDEX idx_khuluma_meetadm_session ON khuluma_meeting_admissions (rtc_session_id);
CREATE INDEX idx_khuluma_meetadm_actor   ON khuluma_meeting_admissions (tenant_id, actor_id);

-- ── Meeting action items (post-session FOLLOW_UP artifacts) ──────────────────
CREATE TABLE khuluma_meeting_action_items (
    action_item_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    conversation_id       UUID            NOT NULL REFERENCES khuluma_conversations (conversation_id),
    description           TEXT            NOT NULL,
    assignee_id           VARCHAR(255),
    assignee_display_name VARCHAR(255),
    due_date              DATE,
    status                VARCHAR(32)     NOT NULL DEFAULT 'OPEN',    -- OPEN|IN_PROGRESS|DONE|CANCELLED
    created_by            VARCHAR(255)    NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_khuluma_meetact_conv     ON khuluma_meeting_action_items (conversation_id, status);
CREATE INDEX idx_khuluma_meetact_assignee ON khuluma_meeting_action_items (tenant_id, assignee_id, status);
