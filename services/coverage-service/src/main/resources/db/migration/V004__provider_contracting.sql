-- ============================================================================
-- coverage-service V004 — Provider contracting & network management (v1.3)
-- ============================================================================

CREATE TABLE IF NOT EXISTS cv_provider_contracts (
    id              BIGSERIAL PRIMARY KEY,
    contract_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    provider_id     VARCHAR(128) NOT NULL,
    payer_id        VARCHAR(128) NOT NULL,
    plan_code       VARCHAR(64),
    contract_type   VARCHAR(32) NOT NULL DEFAULT 'FEE_FOR_SERVICE',
    reimbursement_mode VARCHAR(32) NOT NULL DEFAULT 'FEE_FOR_SERVICE',
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    rate_schedule_json JSONB DEFAULT '{}',
    terms_json      JSONB DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    suspended_at    TIMESTAMPTZ,
    suspension_reason TEXT,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cv_contracts_provider ON cv_provider_contracts (provider_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_cv_contracts_payer ON cv_provider_contracts (payer_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_cv_contracts_active ON cv_provider_contracts (status) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS cv_provider_networks (
    id              BIGSERIAL PRIMARY KEY,
    network_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    payer_id        VARCHAR(128) NOT NULL,
    network_type    VARCHAR(32) NOT NULL DEFAULT 'PREFERRED',
    service_area    JSONB DEFAULT '[]',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cv_network_members (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    network_id      UUID NOT NULL REFERENCES cv_provider_networks(network_id),
    provider_id     VARCHAR(128) NOT NULL,
    contract_id     UUID REFERENCES cv_provider_contracts(contract_id),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at      TIMESTAMPTZ,
    CONSTRAINT uq_cv_network_member UNIQUE (network_id, provider_id)
);
