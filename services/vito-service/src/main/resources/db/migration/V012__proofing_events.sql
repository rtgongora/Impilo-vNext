-- V012: Proofing events — tracks identity verification chain
-- Each proofing event records the method, assurance level, and evidence.

CREATE TABLE IF NOT EXISTS vito.proofing_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    health_id       UUID        NOT NULL REFERENCES vito.client(health_id),
    method          VARCHAR(50) NOT NULL, -- assisted, portal, mosip_indirect, biometric, document
    assurance_level INTEGER     NOT NULL DEFAULT 0, -- 0=none, 1=low, 2=substantial, 3=high
    artifact_refs   JSONB       DEFAULT '[]',
    verifier_id     VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, FAILED, EXPIRED
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_proofing_tenant_health ON vito.proofing_event (tenant_id, health_id);
CREATE INDEX idx_proofing_tenant_method ON vito.proofing_event (tenant_id, method, status);
