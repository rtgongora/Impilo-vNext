-- ============================================================================
-- data-governance-service V003 — Access policies + grant revocation support
-- ============================================================================

-- Access policy registry — versioned governance policies
CREATE TABLE dgv_policy (
    policy_id           UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    version             INT             NOT NULL DEFAULT 1,
    description         VARCHAR(1024),
    rules_json          JSONB           NOT NULL DEFAULT '{}',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dgv_policy_version UNIQUE (tenant_id, name, version)
);

CREATE INDEX idx_dgv_policy_tenant ON dgv_policy (tenant_id);
CREATE INDEX idx_dgv_policy_status ON dgv_policy (tenant_id, status);

-- Add revoked_at and revoked_by columns to grants
ALTER TABLE dgv_grant ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE dgv_grant ADD COLUMN revoked_by VARCHAR(255);
