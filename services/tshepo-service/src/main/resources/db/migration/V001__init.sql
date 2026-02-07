-- TSHEPO — Trust & Governance: Base schema
-- Stores policy decisions, session metadata, and rate-limit state.

CREATE SCHEMA IF NOT EXISTS tshepo;

CREATE TABLE tshepo.policy_decision_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    correlation_id  UUID        NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    actor_type      VARCHAR(50)  NOT NULL,
    action          VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(255) NOT NULL,
    resource_id     VARCHAR(255),
    purpose_of_use  VARCHAR(100) NOT NULL,
    facility_id     UUID,
    workspace_id    UUID,
    decision        VARCHAR(20)  NOT NULL,  -- ALLOW, DENY, STEP_UP_REQUIRED
    obligations     JSONB,
    risk_score      SMALLINT,
    decided_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    duration_ms     INTEGER
);

CREATE INDEX idx_policy_log_tenant_time ON tshepo.policy_decision_log (tenant_id, decided_at DESC);
CREATE INDEX idx_policy_log_actor       ON tshepo.policy_decision_log (tenant_id, actor_id, decided_at DESC);
