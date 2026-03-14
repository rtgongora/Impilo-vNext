-- Governance rules — explicit allow/deny rules for export requests
CREATE TABLE dgv_governance_rule (
    rule_id             UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         VARCHAR(1024),
    resource_pattern    VARCHAR(255)    NOT NULL,
    action              VARCHAR(64)     NOT NULL DEFAULT 'EXPORT',
    effect              VARCHAR(16)     NOT NULL DEFAULT 'DENY',
    required_purpose    VARCHAR(64),
    priority            INT             NOT NULL DEFAULT 100,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dgv_rule_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_dgv_rule_tenant ON dgv_governance_rule (tenant_id);
CREATE INDEX idx_dgv_rule_resource ON dgv_governance_rule (resource_pattern);
