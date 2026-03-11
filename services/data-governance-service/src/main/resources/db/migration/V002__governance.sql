-- ============================================================================
-- data-governance-service V002 — Governance domain tables
-- Dataset registry, access grants, and decision audit log.
-- ============================================================================

-- Dataset registry — cataloged datasets available for governance
CREATE TABLE dgv_dataset (
    dataset_id          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    classification      VARCHAR(64)     NOT NULL DEFAULT 'INTERNAL',
    description         VARCHAR(1024),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dgv_dataset_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_dgv_dataset_tenant ON dgv_dataset (tenant_id);

-- Access grants — principal → dataset with purpose limitation and expiry
CREATE TABLE dgv_grant (
    grant_id            UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dataset_id          UUID            NOT NULL REFERENCES dgv_dataset(dataset_id),
    principal_id        VARCHAR(255)    NOT NULL,
    purpose_of_use      VARCHAR(64)     NOT NULL,
    expires_at          TIMESTAMPTZ,
    revoked             BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dgv_grant_unique
        UNIQUE (tenant_id, dataset_id, principal_id, purpose_of_use)
);

CREATE INDEX idx_dgv_grant_principal ON dgv_grant (principal_id);
CREATE INDEX idx_dgv_grant_dataset ON dgv_grant (dataset_id);

-- Decision audit log — every allow/deny is recorded for compliance
CREATE TABLE dgv_decision_audit (
    decision_id         UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dataset_id          UUID,
    dataset_name        VARCHAR(255)    NOT NULL,
    principal_id        VARCHAR(255)    NOT NULL,
    decision            VARCHAR(16)     NOT NULL,
    reason_codes_json   TEXT            NOT NULL DEFAULT '[]',
    policy_version      VARCHAR(64)     NOT NULL,
    query_fingerprint   VARCHAR(255),
    correlation_id      VARCHAR(255),
    decided_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dgv_decision_principal ON dgv_decision_audit (principal_id);
CREATE INDEX idx_dgv_decision_dataset ON dgv_decision_audit (dataset_name);
CREATE INDEX idx_dgv_decision_decided_at ON dgv_decision_audit (decided_at);
