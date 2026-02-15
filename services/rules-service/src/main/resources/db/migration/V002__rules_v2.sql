-- V002: Upgrade rules-service to v2 — registry, versioning, activation, and evaluation audit

-- Add key and status columns to existing rules table
ALTER TABLE rs_rules ADD COLUMN key VARCHAR(128);
ALTER TABLE rs_rules ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- Make expression nullable (v2 rules store DSL in rule_versions)
ALTER TABLE rs_rules ALTER COLUMN expression DROP NOT NULL;

-- Unique constraint on tenant_id + key for v2 rules
CREATE UNIQUE INDEX idx_rs_rules_tenant_key ON rs_rules (tenant_id, key) WHERE key IS NOT NULL;

-- Rule versions — each rule can have multiple versioned DSL expressions
CREATE TABLE rs_rule_versions (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    rule_id    VARCHAR(36)  NOT NULL,
    version    INT          NOT NULL,
    dsl_text   TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(rule_id, version)
);
CREATE INDEX idx_rs_rule_versions_rule ON rs_rule_versions (rule_id);

-- Rule activations — tracks which version is active for each rule
CREATE TABLE rs_rule_activations (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    rule_id     VARCHAR(36)  NOT NULL,
    version_id  VARCHAR(36)  NOT NULL,
    active_from TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active_to   TIMESTAMPTZ
);
CREATE INDEX idx_rs_rule_activations_rule   ON rs_rule_activations (rule_id);
CREATE INDEX idx_rs_rule_activations_active ON rs_rule_activations (rule_id) WHERE active_to IS NULL;

-- Evaluation audit — records every evaluation for audit trail
CREATE TABLE rs_evaluation_audit (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    rule_key       VARCHAR(128) NOT NULL,
    version        INT          NOT NULL,
    input_hash     VARCHAR(64)  NOT NULL,
    result_json    TEXT         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tenant_id      VARCHAR(64)  NOT NULL,
    pod_id         VARCHAR(64)  NOT NULL,
    correlation_id VARCHAR(64)  NOT NULL
);
CREATE INDEX idx_rs_eval_audit_rule   ON rs_evaluation_audit (rule_key, tenant_id);
CREATE INDEX idx_rs_eval_audit_tenant ON rs_evaluation_audit (tenant_id, occurred_at DESC);
