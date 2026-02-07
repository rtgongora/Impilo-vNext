-- TSHEPO Authz — Authorization & Policy Enforcement schema
-- This is the core schema for ABAC/RBAC policy rules, decision logging,
-- device risk profiles, break-glass requests, step-up challenges, and
-- the transactional outbox for reliable event publishing.

CREATE SCHEMA IF NOT EXISTS tshepo_authz;

-- ═══════════════════════════════════════════════════════════════════════
-- 1. Policy Rules — ABAC/RBAC rule definitions
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.policy_rule (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    actor_type      VARCHAR(32),
    role            VARCHAR(64),
    resource_type   VARCHAR(64),
    action          VARCHAR(32),
    purpose         VARCHAR(32),
    facility_scope  BOOLEAN         NOT NULL DEFAULT false,
    workspace_scope BOOLEAN         NOT NULL DEFAULT false,
    effect          VARCHAR(8)      NOT NULL DEFAULT 'ALLOW',
    priority        INT             NOT NULL DEFAULT 100,
    conditions      JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_policy_rule_tenant_active
    ON tshepo_authz.policy_rule (tenant_id, active)
    WHERE active = true;

CREATE INDEX idx_policy_rule_lookup
    ON tshepo_authz.policy_rule (tenant_id, resource_type, active)
    WHERE active = true;

CREATE INDEX idx_policy_rule_actor_type
    ON tshepo_authz.policy_rule (tenant_id, actor_type, active)
    WHERE active = true;

-- ═══════════════════════════════════════════════════════════════════════
-- 2. Policy Decision Log — immutable audit of every authorization decision
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.policy_decision_log (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    correlation_id      UUID            NOT NULL,
    actor_id            VARCHAR(255)    NOT NULL,
    actor_type          VARCHAR(32),
    action              VARCHAR(64)     NOT NULL,
    resource_type       VARCHAR(64),
    resource_id         VARCHAR(255),
    purpose_of_use      VARCHAR(32),
    verdict             VARCHAR(16)     NOT NULL,
    risk_score          INT,
    obligations         JSONB,
    deny_reason         TEXT,
    step_up_methods     TEXT,
    device_fingerprint  VARCHAR(255),
    facility_id         UUID,
    workspace_id        UUID,
    evaluated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_decision_log_tenant_time
    ON tshepo_authz.policy_decision_log (tenant_id, evaluated_at DESC);

CREATE INDEX idx_decision_log_actor
    ON tshepo_authz.policy_decision_log (tenant_id, actor_id, evaluated_at DESC);

CREATE INDEX idx_decision_log_correlation
    ON tshepo_authz.policy_decision_log (correlation_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 3. Device Profile — device reputation and risk tracking
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.device_profile (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    fingerprint     VARCHAR(255)    NOT NULL,
    actor_id        VARCHAR(255),
    risk_score      INT             NOT NULL DEFAULT 0,
    last_seen_at    TIMESTAMPTZ,
    blocked         BOOLEAN         NOT NULL DEFAULT false,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, fingerprint)
);

CREATE INDEX idx_device_profile_actor
    ON tshepo_authz.device_profile (tenant_id, actor_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 4. Break-Glass Request — emergency access override with mandatory review
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.break_glass_request (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    reason          TEXT            NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(255),
    approved        BOOLEAN,
    review_status   VARCHAR(16)     NOT NULL DEFAULT 'PENDING_REVIEW',
    granted_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ
);

CREATE INDEX idx_break_glass_tenant_actor
    ON tshepo_authz.break_glass_request (tenant_id, actor_id, granted_at DESC);

CREATE INDEX idx_break_glass_review_status
    ON tshepo_authz.break_glass_request (tenant_id, review_status)
    WHERE review_status = 'PENDING_REVIEW';

-- ═══════════════════════════════════════════════════════════════════════
-- 5. Step-Up Challenge — MFA / biometric / supervisor approval challenges
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.step_up_challenge (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    challenge_type  VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_step_up_tenant_actor
    ON tshepo_authz.step_up_challenge (tenant_id, actor_id, issued_at DESC);

CREATE INDEX idx_step_up_status
    ON tshepo_authz.step_up_challenge (tenant_id, status, expires_at)
    WHERE status = 'PENDING';

-- ═══════════════════════════════════════════════════════════════════════
-- 6. Event Outbox — transactional outbox for reliable Kafka publishing
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE tshepo_authz.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished
    ON tshepo_authz.event_outbox (created_at)
    WHERE published_at IS NULL;
