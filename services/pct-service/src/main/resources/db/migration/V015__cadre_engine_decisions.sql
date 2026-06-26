-- Cadre Engine decision audit (shared read-model C9).
-- Every Cadre Engine resolution is audited: the inputs, the resolved workflow set, and the
-- adaptive cockpit spine are persisted so a clinical action's permitted-workflow basis is replayable.
-- This table is an AUDIT LOG, not a system-of-record for clinical state.

CREATE TABLE pct_cadre_decisions (
    audit_ref            UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    actor_id            VARCHAR(255)     NOT NULL,
    correlation_id       UUID,
    journey_id           VARCHAR(26),
    encounter_id         VARCHAR(64),

    -- Inputs (the C9 request)
    in_role              VARCHAR(128),
    in_cadre             VARCHAR(128),
    in_scope             VARCHAR(64),
    in_visit_type        VARCHAR(64),
    in_acuity            INTEGER,
    in_context           VARCHAR(32),
    in_access_state      VARCHAR(64),

    -- Outputs (the C9 decision)
    permitted_workflows  JSONB           NOT NULL,
    cockpit_spine        JSONB           NOT NULL,
    escalation           JSONB           NOT NULL,

    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_cadre_decisions_tenant_actor ON pct_cadre_decisions (tenant_id, actor_id);
CREATE INDEX idx_pct_cadre_decisions_journey ON pct_cadre_decisions (tenant_id, journey_id);
CREATE INDEX idx_pct_cadre_decisions_created ON pct_cadre_decisions (created_at);

COMMENT ON TABLE pct_cadre_decisions IS
    'Audit log of PCT Cadre Engine resolutions (shared read-model C9). Replayable basis for every adaptive cockpit decision.';
COMMENT ON COLUMN pct_cadre_decisions.permitted_workflows IS
    'Resolved permitted-workflow set (JSON array of workflow codes).';
COMMENT ON COLUMN pct_cadre_decisions.cockpit_spine IS
    'Resolved adaptive cockpit spine (JSON array of tabs+actions) the Encounter Cockpit renders from.';
