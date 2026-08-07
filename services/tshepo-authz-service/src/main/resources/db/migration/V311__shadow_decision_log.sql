-- ============================================================================
-- V311 — shadow_decision_log: what authorization WOULD decide if the PDP could
--        see a browser session's roles
--
-- Measured 2026-08-07 against the live estate: the browser holds an opaque BFF
-- session cookie and sends no Authorization header, so the PDP validates no
-- session and resolves no roles — while 489 of 525 active ALLOW rules (93%)
-- require one. The RBAC layer is therefore inert on the primary human path.
--
-- Turning that on blind is not available to us. Rules that have never fired once
-- would begin firing, DENY rules included, against traffic that works today. This
-- table is how we learn the blast radius before anything changes.
--
-- ── What this table is NOT ──────────────────────────────────────────────────
-- It is not an authorization record and must never be read by an enforcement
-- path. policy_decision_log remains the only record of what was actually
-- decided. Nothing here ever alters a verdict.
--
-- ── The token is not here, and cannot be ────────────────────────────────────
-- The shadow evaluation requires the person's access token to resolve roles.
-- That token is transient memory in the PDP for the length of one evaluation:
-- it is never written to this table, never logged, never traced. Only DERIVED
-- facts land here — roles, persona, verdicts, deltas. There is deliberately no
-- column it could be stored in.
--
-- ── Isolation ───────────────────────────────────────────────────────────────
-- No foreign key into policy_decision_log or policy_rule. A write here must not
-- be able to fail, lock or slow a real decision, and a constraint against a
-- table the enforcement path writes would couple exactly the two things that
-- must stay uncoupled. Correlation is by correlation_id, joined offline.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tshepo_authz.shadow_decision_log (
    id                      BIGSERIAL    PRIMARY KEY,
    correlation_id          UUID,
    tenant_id               UUID,
    actor_id                VARCHAR(128),

    -- The request, captured so production and shadow are provably the same input.
    method                  VARCHAR(10),
    path                    TEXT,
    resource_type           VARCHAR(128),
    action                  VARCHAR(64),

    -- What actually happened (from Envoy's ext_authz decision, already final).
    prod_verdict            VARCHAR(16),
    prod_actor_type         VARCHAR(32),

    -- What would have happened with bearer-derived roles.
    shadow_verdict          VARCHAR(16),
    shadow_deny_reason      VARCHAR(64),
    shadow_matched_rule     VARCHAR(128),
    shadow_roles            TEXT,          -- comma-joined; derived, never a credential

    -- The principal model this stage exists to validate.
    principal_class         VARCHAR(16),   -- HUMAN | SERVICE | SYSTEM
    active_persona          VARCHAR(48),   -- MY_LIFE | MY_PROFESSIONAL | PROXY | <WorkMode>
    legacy_actor_type       VARCHAR(32),   -- transitional projection from the proven context

    -- The answer we are actually here for.
    delta_kind              VARCHAR(32)    NOT NULL,
    obligations_delta       TEXT,

    -- Mode + health, so a missing probe is measurable rather than invisible.
    probe_mode              VARCHAR(16),   -- ASYNC | INLINE_CANARY
    probe_outcome           VARCHAR(24)    NOT NULL, -- OK | PDP_ERROR | TIMEOUT | BEARER_UNRESOLVED | RECORDER_ERROR
    pdp_millis              INTEGER,
    total_added_millis      INTEGER,
    route_class             VARCHAR(64),

    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shadow_decision_delta
    ON tshepo_authz.shadow_decision_log (delta_kind, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shadow_decision_correlation
    ON tshepo_authz.shadow_decision_log (correlation_id);
CREATE INDEX IF NOT EXISTS idx_shadow_decision_persona
    ON tshepo_authz.shadow_decision_log (active_persona, delta_kind);
CREATE INDEX IF NOT EXISTS idx_shadow_decision_outcome
    ON tshepo_authz.shadow_decision_log (probe_outcome, created_at DESC);

COMMENT ON TABLE tshepo_authz.shadow_decision_log IS
    'P1 shadow measurement only. Never read by an enforcement path. Holds derived facts about what authorization WOULD decide with bearer-derived roles; never holds a credential.';
COMMENT ON COLUMN tshepo_authz.shadow_decision_log.delta_kind IS
    'NONE | PERMIT_TO_DENY | DENY_TO_PERMIT | ACTOR_TYPE_CHANGE | RULE_CHANGE — PERMIT_TO_DENY is the one that would break live traffic at enforcement.';
COMMENT ON COLUMN tshepo_authz.shadow_decision_log.probe_outcome IS
    'Records infrastructure failure explicitly, so probes that never ran are countable rather than silently absent.';
