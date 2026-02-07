-- V015: Dedup actions (audit trail) and merge history (provenance)
-- Every dedup decision is logged. Every merge is reversible with full provenance.

-- Dedup actions: audit trail for each case decision
CREATE TABLE IF NOT EXISTS vito.dedup_action (
    id          BIGSERIAL PRIMARY KEY,
    case_id     BIGINT       NOT NULL REFERENCES vito.dedup_case(id),
    tenant_id   UUID         NOT NULL,
    actor_id    VARCHAR(255) NOT NULL,
    actor_type  VARCHAR(50)  NOT NULL, -- OPERATOR, SYSTEM, SUPERVISOR
    action      VARCHAR(50)  NOT NULL, -- ASSIGNED, REVIEWED, LINKED, REJECTED, DEFERRED, ESCALATED
    payload     JSONB        DEFAULT '{}',
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dedup_action_case ON vito.dedup_action (case_id, created_at);
CREATE INDEX idx_dedup_action_actor ON vito.dedup_action (tenant_id, actor_id);

-- Merge history: full provenance for record merges
CREATE TABLE IF NOT EXISTS vito.merge_history (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    survivor_crid   UUID         NOT NULL, -- the record that survives
    merged_crid     UUID         NOT NULL, -- the record that was absorbed
    dedup_case_id   BIGINT       REFERENCES vito.dedup_case(id),
    strategy        VARCHAR(50)  NOT NULL, -- AUTO, MANUAL, RULE_BASED
    field_decisions JSONB        NOT NULL DEFAULT '{}', -- {field: "survivor"|"merged"|"custom", ...}
    reversible      BOOLEAN      NOT NULL DEFAULT true,
    reversed_at     TIMESTAMPTZ,
    reversed_by     VARCHAR(255),
    created_by_id   VARCHAR(255) NOT NULL,
    created_by_type VARCHAR(50)  NOT NULL, -- OPERATOR, SYSTEM
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_merge_survivor ON vito.merge_history (tenant_id, survivor_crid);
CREATE INDEX idx_merge_merged ON vito.merge_history (tenant_id, merged_crid);
CREATE INDEX idx_merge_reversible ON vito.merge_history (tenant_id) WHERE reversed_at IS NULL AND reversible = true;

-- Also update dedup_case to support full workflow states from spec
ALTER TABLE vito.dedup_case
    ADD COLUMN IF NOT EXISTS risk VARCHAR(20) DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS reasons JSONB DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(255);
