-- ============================================================================
-- channels-service V001 — Initial schema (v1.1 compliant)
-- Tables: ch_event_outbox, idempotency_keys, ch_sessions, ch_messages,
--         ch_assisted_interactions
-- ============================================================================

-- v1.1 Event Outbox (canonical schema per EVENTING_AND_TOPICS.md §2.2)
CREATE TABLE ch_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      INT             NOT NULL DEFAULT 1,
    correlation_id      VARCHAR(64),
    causation_id        VARCHAR(64),
    idempotency_key     VARCHAR(255),
    producer            VARCHAR(64)     NOT NULL DEFAULT 'channels-service',
    tenant_id           VARCHAR(64)     NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        TEXT            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ
);

CREATE INDEX idx_ch_outbox_unpublished
    ON ch_event_outbox (created_at)
    WHERE published_at IS NULL;

-- Idempotency keys (per tech-companion spec)
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ch_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- Channel Sessions — tracks omnichannel interaction sessions
CREATE TABLE ch_sessions (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    channel_type    VARCHAR(32)     NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    subject_ref     VARCHAR(255),
    agent_id        VARCHAR(255),
    facility_id     VARCHAR(255),
    metadata_json   TEXT,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_activity   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ch_sessions_tenant ON ch_sessions (tenant_id);
CREATE INDEX idx_ch_sessions_status ON ch_sessions (status) WHERE status != 'CLOSED';

-- Channel Messages — tracks individual messages within sessions
CREATE TABLE ch_messages (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id      UUID            NOT NULL REFERENCES ch_sessions(id),
    tenant_id       VARCHAR(64)     NOT NULL,
    direction       VARCHAR(16)     NOT NULL,
    channel_type    VARCHAR(32)     NOT NULL,
    content_type    VARCHAR(32)     NOT NULL DEFAULT 'TEXT',
    payload_json    TEXT            NOT NULL,
    sender_id       VARCHAR(255),
    delivery_status VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ch_messages_session ON ch_messages (session_id);

-- Assisted Interactions — agent-assisted flows
CREATE TABLE ch_assisted_interactions (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    session_id      UUID            NOT NULL REFERENCES ch_sessions(id),
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    agent_id        VARCHAR(255)    NOT NULL,
    client_id       VARCHAR(255),
    notes_json      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ch_assisted_session ON ch_assisted_interactions (session_id);
