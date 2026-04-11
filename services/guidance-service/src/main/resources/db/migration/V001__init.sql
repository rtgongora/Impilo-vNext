-- =============================================================================
-- guidance-service V001 — Core schema (Health OS §13)
--
-- Knowledge articles, guidance sessions, reminders, education content.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS guidance;

-- Knowledge articles — governed health/wellness/lifestyle content
CREATE TABLE guidance.knowledge_articles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    title           VARCHAR(512)    NOT NULL,
    summary         TEXT            NOT NULL,
    content         TEXT            NOT NULL,
    category        VARCHAR(64)     NOT NULL,
    domain          VARCHAR(64)     NOT NULL DEFAULT 'health',
    tags            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    source          VARCHAR(255),
    source_url      VARCHAR(1024),
    status          VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED',
    relevance_context JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ka_tenant_domain ON guidance.knowledge_articles (tenant_id, domain);
CREATE INDEX idx_ka_category ON guidance.knowledge_articles (category);
CREATE INDEX idx_ka_status ON guidance.knowledge_articles (status);

-- Guidance sessions — conversational interaction history
CREATE TABLE guidance.guidance_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    message_count   INTEGER         NOT NULL DEFAULT 0,
    personalized    BOOLEAN         NOT NULL DEFAULT FALSE,
    context_json    JSONB,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_gs_actor ON guidance.guidance_sessions (tenant_id, actor_id);

-- Guidance messages — individual Q&A exchanges
CREATE TABLE guidance.guidance_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID            NOT NULL REFERENCES guidance.guidance_sessions(id),
    role            VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    sources_json    JSONB,
    confidence      REAL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gm_session ON guidance.guidance_messages (session_id);

-- Reminders — proactive prompts for the user
CREATE TABLE guidance.reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    reminder_type   VARCHAR(32)     NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    due_date        DATE,
    priority        VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    dismissed_at    TIMESTAMPTZ,
    source_ref      VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rem_actor ON guidance.reminders (tenant_id, actor_id, status);

-- Event outbox
CREATE TABLE guidance.event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(128)    NOT NULL,
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id  UUID            NOT NULL DEFAULT gen_random_uuid(),
    producer        VARCHAR(128)    NOT NULL DEFAULT 'guidance-service',
    tenant_id       VARCHAR(255)    NOT NULL,
    subject_type    VARCHAR(128),
    subject_id      VARCHAR(255),
    payload         JSONB           NOT NULL,
    meta            JSONB           NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpub ON guidance.event_outbox (published_at) WHERE published_at IS NULL;
