-- =============================================
-- Service : live-service (Impilo Live)
-- Schema  : live
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS live;

CREATE TABLE live.live_events (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL,
    title                       VARCHAR(512)    NOT NULL,
    description                 TEXT,
    event_type                  VARCHAR(64)     NOT NULL,
    context_type                VARCHAR(32)     NOT NULL DEFAULT 'PROFESSIONAL',
    audience_type               VARCHAR(64)     NOT NULL DEFAULT 'ROLE_BASED',
    visibility                  VARCHAR(32)     NOT NULL DEFAULT 'PRIVATE',
    status                      VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    organiser_type              VARCHAR(32)     NOT NULL,
    organiser_id                VARCHAR(255)    NOT NULL,
    facility_id                 VARCHAR(255),
    district_id                 VARCHAR(255),
    province_id                 VARCHAR(255),
    programme_id                VARCHAR(255),
    linked_service_id           VARCHAR(255),
    linked_campaign_id          VARCHAR(255),
    linked_fundo_course_id      UUID,
    linked_madi_drive_id        UUID,
    start_time                  TIMESTAMPTZ,
    end_time                    TIMESTAMPTZ,
    timezone                    VARCHAR(64)     DEFAULT 'Africa/Harare',
    language                    VARCHAR(16)     DEFAULT 'en',
    accessibility_options       JSONB           DEFAULT '[]'::jsonb,
    registration_required       BOOLEAN         NOT NULL DEFAULT true,
    max_participants            INTEGER,
    recording_allowed           BOOLEAN         NOT NULL DEFAULT false,
    replay_allowed              BOOLEAN         NOT NULL DEFAULT false,
    consent_required            BOOLEAN         NOT NULL DEFAULT false,
    chat_enabled                BOOLEAN         NOT NULL DEFAULT true,
    qna_enabled                 BOOLEAN         NOT NULL DEFAULT true,
    polls_enabled               BOOLEAN         NOT NULL DEFAULT true,
    cpd_enabled                 BOOLEAN         NOT NULL DEFAULT false,
    cpd_points                  NUMERIC(6,2),
    attendance_threshold_minutes INTEGER        DEFAULT 30,
    agenda                      TEXT,
    objectives                  TEXT,
    moderation_settings         JSONB           DEFAULT '{}'::jsonb,
    created_by                  VARCHAR(255)    NOT NULL,
    updated_by                  VARCHAR(255),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ
);

CREATE INDEX idx_live_events_tenant_status ON live.live_events (tenant_id, status, start_time);
CREATE INDEX idx_live_events_context ON live.live_events (tenant_id, context_type, status);
CREATE INDEX idx_live_events_facility ON live.live_events (tenant_id, facility_id, status);
CREATE INDEX idx_live_events_fundo ON live.live_events (linked_fundo_course_id) WHERE linked_fundo_course_id IS NOT NULL;
CREATE INDEX idx_live_events_madi ON live.live_events (linked_madi_drive_id) WHERE linked_madi_drive_id IS NOT NULL;

CREATE TABLE live.live_event_role_assignments (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    user_id         VARCHAR(255)    NOT NULL,
    participant_type VARCHAR(32)    NOT NULL DEFAULT 'PROVIDER',
    role            VARCHAR(32)     NOT NULL,
    assigned_by     VARCHAR(255)    NOT NULL,
    assigned_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id, role)
);

CREATE INDEX idx_live_roles_event ON live.live_event_role_assignments (event_id);

CREATE TABLE live.live_event_registrations (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    participant_type    VARCHAR(32)     NOT NULL,
    registration_status VARCHAR(32)     NOT NULL DEFAULT 'REGISTERED',
    registered_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    cancelled_at        TIMESTAMPTZ,
    invite_source       VARCHAR(64),
    reminders_enabled   BOOLEAN         NOT NULL DEFAULT true,
    saved               BOOLEAN         NOT NULL DEFAULT false,
    UNIQUE (event_id, participant_id)
);

CREATE INDEX idx_live_registrations_event ON live.live_event_registrations (event_id, registration_status);
CREATE INDEX idx_live_registrations_participant ON live.live_event_registrations (participant_id, registration_status);

CREATE TABLE live.live_event_sessions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    provider_type       VARCHAR(32)     NOT NULL DEFAULT 'RTC_GATEWAY',
    provider_room_id      VARCHAR(255),
    stream_key_ref        VARCHAR(255),
    playback_url_ref      VARCHAR(512),
    recording_ref         VARCHAR(512),
    started_at            TIMESTAMPTZ,
    ended_at              TIMESTAMPTZ,
    health_status         VARCHAR(32)     DEFAULT 'UNKNOWN',
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_sessions_event ON live.live_event_sessions (event_id);

CREATE TABLE live.live_event_attendance (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id          VARCHAR(255)    NOT NULL,
    participant_type        VARCHAR(32)     NOT NULL,
    joined_at               TIMESTAMPTZ,
    left_at                 TIMESTAMPTZ,
    total_watch_minutes     INTEGER         NOT NULL DEFAULT 0,
    live_watch_minutes      INTEGER         NOT NULL DEFAULT 0,
    replay_watch_minutes    INTEGER         NOT NULL DEFAULT 0,
    completion_status       VARCHAR(32)     NOT NULL DEFAULT 'IN_PROGRESS',
    eligible_for_certificate BOOLEAN        NOT NULL DEFAULT false,
    eligible_for_cpd        BOOLEAN         NOT NULL DEFAULT false,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (event_id, participant_id)
);

CREATE INDEX idx_live_attendance_event ON live.live_event_attendance (event_id);
CREATE INDEX idx_live_attendance_participant ON live.live_event_attendance (participant_id);

CREATE TABLE live.live_event_questions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    participant_type    VARCHAR(32)     NOT NULL,
    question_text       TEXT            NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    anonymous_allowed   BOOLEAN         NOT NULL DEFAULT false,
    answered_by         VARCHAR(255),
    answered_at         TIMESTAMPTZ,
    upvotes             INTEGER         NOT NULL DEFAULT 0,
    pinned              BOOLEAN         NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_questions_event ON live.live_event_questions (event_id, status);

CREATE TABLE live.live_event_chat_messages (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    participant_type    VARCHAR(32)     NOT NULL,
    message             TEXT            NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'VISIBLE',
    moderated_by        VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_chat_event ON live.live_event_chat_messages (event_id, created_at);

CREATE TABLE live.live_event_polls (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    question            TEXT            NOT NULL,
    options             JSONB           NOT NULL DEFAULT '[]'::jsonb,
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_polls_event ON live.live_event_polls (event_id, status);

CREATE TABLE live.live_event_poll_responses (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id             UUID            NOT NULL REFERENCES live.live_event_polls(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    selected_option     VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (poll_id, participant_id)
);

CREATE TABLE live.live_event_resources (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    file_id             VARCHAR(255),
    resource_type       VARCHAR(64)     NOT NULL DEFAULT 'DOCUMENT',
    title               VARCHAR(512)    NOT NULL,
    visibility          VARCHAR(32)     NOT NULL DEFAULT 'ATTENDEES',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_resources_event ON live.live_event_resources (event_id);

CREATE TABLE live.live_event_feedback (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    rating              INTEGER,
    comments            TEXT,
    submitted_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (event_id, participant_id)
);

CREATE TABLE live.live_event_certificates (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    participant_id      VARCHAR(255)    NOT NULL,
    certificate_type    VARCHAR(64)     NOT NULL,
    certificate_ref       VARCHAR(255),
    verification_code     VARCHAR(64)     NOT NULL,
    issued_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (event_id, participant_id, certificate_type)
);

CREATE INDEX idx_live_certificates_participant ON live.live_event_certificates (participant_id);

CREATE TABLE live.live_event_analytics_snapshots (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID            NOT NULL REFERENCES live.live_events(id) ON DELETE CASCADE,
    metric_name         VARCHAR(128)    NOT NULL,
    metric_value        NUMERIC(18,4)   NOT NULL,
    dimensions          JSONB           DEFAULT '{}'::jsonb,
    captured_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_live_analytics_event ON live.live_event_analytics_snapshots (event_id, metric_name, captured_at DESC);

CREATE TABLE live.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    subject_type    VARCHAR(64),
    subject_id      VARCHAR(255),
    tenant_id       UUID,
    pod_id          VARCHAR(64),
    correlation_id  UUID,
    partition_key   VARCHAR(255),
    idempotency_key VARCHAR(512),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_live_outbox_unpublished ON live.event_outbox (created_at) WHERE published_at IS NULL;
