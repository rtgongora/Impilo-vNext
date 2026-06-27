-- ============================================================================
-- khuluma-service V002 — Conversation / message / presence / call domain
-- The missing systems-of-record Khuluma owns: the unified conversation index,
-- message + receipts, conversation↔object links, presence, and call lifecycle.
-- Media stays in rtc-gateway/LiveKit; notifications stay in notification-service.
-- ============================================================================

-- ── Conversations (unified index over all interaction surfaces) ─────────────
CREATE TABLE khuluma_conversations (
    conversation_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    conversation_type     VARCHAR(32)     NOT NULL DEFAULT 'DIRECT',  -- DIRECT|GROUP|CHANNEL|BROADCAST
    title                 VARCHAR(255),
    topic                 VARCHAR(512),
    created_by            VARCHAR(255)    NOT NULL,
    created_by_type       VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    status                VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|ARCHIVED|CLOSED
    last_message_id       UUID,
    last_message_preview  VARCHAR(512),
    last_message_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_khuluma_conv_tenant_lastmsg
    ON khuluma_conversations (tenant_id, last_message_at DESC);

-- ── Participants (drives the inbox + unread + access membership) ─────────────
CREATE TABLE khuluma_conversation_participants (
    participant_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID            NOT NULL REFERENCES khuluma_conversations (conversation_id),
    tenant_id             UUID            NOT NULL,
    actor_id              VARCHAR(255)    NOT NULL,
    actor_type            VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    display_name          VARCHAR(255),
    role                  VARCHAR(32)     NOT NULL DEFAULT 'MEMBER',  -- OWNER|MODERATOR|MEMBER
    joined_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    left_at               TIMESTAMPTZ,
    last_read_at          TIMESTAMPTZ,
    last_read_message_id  UUID,
    muted                 BOOLEAN         NOT NULL DEFAULT FALSE,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_khuluma_participant UNIQUE (conversation_id, actor_id)
);

CREATE INDEX idx_khuluma_partic_conv   ON khuluma_conversation_participants (conversation_id);
CREATE INDEX idx_khuluma_partic_inbox  ON khuluma_conversation_participants (tenant_id, actor_id) WHERE active;

-- ── Messages ────────────────────────────────────────────────────────────────
CREATE TABLE khuluma_messages (
    message_id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID            NOT NULL REFERENCES khuluma_conversations (conversation_id),
    tenant_id             UUID            NOT NULL,
    sender_id             VARCHAR(255)    NOT NULL,
    sender_type           VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    sender_display_name   VARCHAR(255),
    content_type          VARCHAR(32)     NOT NULL DEFAULT 'TEXT',   -- TEXT|SYSTEM|ATTACHMENT
    body                  TEXT            NOT NULL,
    metadata_json         JSONB,
    client_message_id     VARCHAR(255),
    reply_to_message_id   UUID,
    status                VARCHAR(32)     NOT NULL DEFAULT 'SENT',    -- SENT|DELIVERED|READ|DELETED
    sent_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    edited_at             TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT uq_khuluma_msg_client UNIQUE (conversation_id, client_message_id)
);

CREATE INDEX idx_khuluma_msg_conv_sent ON khuluma_messages (conversation_id, sent_at);

-- ── Read / delivery receipts ─────────────────────────────────────────────────
CREATE TABLE khuluma_message_receipts (
    receipt_id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id            UUID            NOT NULL REFERENCES khuluma_messages (message_id),
    conversation_id       UUID            NOT NULL,
    tenant_id             UUID            NOT NULL,
    actor_id              VARCHAR(255)    NOT NULL,
    receipt_state         VARCHAR(32)     NOT NULL,                  -- DELIVERED|READ
    occurred_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_receipt UNIQUE (message_id, actor_id, receipt_state)
);

CREATE INDEX idx_khuluma_receipt_msg   ON khuluma_message_receipts (message_id);
CREATE INDEX idx_khuluma_receipt_actor ON khuluma_message_receipts (actor_id);

-- ── Conversation ↔ canonical-object links (patient/encounter/order/referral…) ─
CREATE TABLE khuluma_conversation_links (
    link_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID            NOT NULL REFERENCES khuluma_conversations (conversation_id),
    tenant_id             UUID            NOT NULL,
    object_type           VARCHAR(64)     NOT NULL,                  -- PATIENT|ENCOUNTER|ORDER|REFERRAL|CASE
    object_id             VARCHAR(255)    NOT NULL,
    link_role             VARCHAR(64),                               -- SUBJECT|CONTEXT
    linked_by             VARCHAR(255)    NOT NULL,
    note                  VARCHAR(512),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_link UNIQUE (conversation_id, object_type, object_id)
);

CREATE INDEX idx_khuluma_links_object ON khuluma_conversation_links (object_type, object_id);
CREATE INDEX idx_khuluma_links_conv   ON khuluma_conversation_links (conversation_id);

-- ── Presence ─────────────────────────────────────────────────────────────────
CREATE TABLE khuluma_presence (
    presence_id           UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    actor_id              VARCHAR(255)    NOT NULL,
    actor_type            VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    status                VARCHAR(32)     NOT NULL DEFAULT 'OFFLINE', -- ONLINE|AWAY|BUSY|DND|OFFLINE
    status_message        VARCHAR(255),
    device                VARCHAR(128),
    last_active_at        TIMESTAMPTZ,
    last_seen_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_presence UNIQUE (tenant_id, actor_id)
);

-- ── Calls (lifecycle context over rtc-gateway/LiveKit media sessions) ────────
CREATE TABLE khuluma_calls (
    call_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID            NOT NULL,
    conversation_id       UUID            REFERENCES khuluma_conversations (conversation_id),
    call_type             VARCHAR(16)     NOT NULL DEFAULT 'AUDIO',   -- AUDIO|VIDEO
    status                VARCHAR(32)     NOT NULL DEFAULT 'RINGING', -- RINGING|ACTIVE|ENDED|DECLINED|MISSED|FAILED|CANCELLED
    initiated_by          VARCHAR(255)    NOT NULL,
    initiated_by_type     VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    rtc_session_id        VARCHAR(255),
    room_name             VARCHAR(255),
    provider              VARCHAR(64),
    end_reason            VARCHAR(64),
    initiated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    started_at            TIMESTAMPTZ,
    ended_at              TIMESTAMPTZ
);

CREATE INDEX idx_khuluma_calls_conv ON khuluma_calls (conversation_id);

CREATE TABLE khuluma_call_participants (
    call_participant_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id               UUID            NOT NULL REFERENCES khuluma_calls (call_id),
    tenant_id             UUID            NOT NULL,
    actor_id              VARCHAR(255)    NOT NULL,
    actor_type            VARCHAR(64)     NOT NULL DEFAULT 'PROVIDER',
    display_name          VARCHAR(255),
    state                 VARCHAR(32)     NOT NULL DEFAULT 'INVITED',  -- INVITED|RINGING|JOINED|DECLINED|LEFT|MISSED
    invited_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    joined_at             TIMESTAMPTZ,
    left_at               TIMESTAMPTZ,
    CONSTRAINT uq_khuluma_call_participant UNIQUE (call_id, actor_id)
);

CREATE INDEX idx_khuluma_callpartic_actor ON khuluma_call_participants (tenant_id, actor_id);
CREATE INDEX idx_khuluma_callpartic_call  ON khuluma_call_participants (call_id);

CREATE TABLE khuluma_call_events (
    call_event_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id               UUID            NOT NULL REFERENCES khuluma_calls (call_id),
    tenant_id             UUID            NOT NULL,
    event_type            VARCHAR(32)     NOT NULL,                  -- RING|ACCEPT|DECLINE|END|SIGNAL|JOIN|LEAVE|CANCEL
    actor_id              VARCHAR(255),
    detail_json           JSONB,
    occurred_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_khuluma_callevents_call ON khuluma_call_events (call_id, occurred_at);
