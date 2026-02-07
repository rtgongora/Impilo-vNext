-- V013: Issuance requests — full state machine for ID issuance workflow
-- State machine: SUBMITTED → PROOFING → APPROVED → ISSUED → DELIVERED
-- Also: REJECTED, EXPIRED (terminal states)

CREATE TABLE IF NOT EXISTS vito.issuance_request (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    health_id           UUID         NOT NULL REFERENCES vito.client(health_id),
    type                VARCHAR(20)  NOT NULL, -- NEW, REPLACEMENT, RECOVERY
    channel             VARCHAR(20)  NOT NULL, -- PORTAL, ASSISTED
    state               VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    risk_score          INTEGER      DEFAULT 0,
    assigned_to         VARCHAR(255),
    rejection_reason    TEXT,
    impilo_id_issued    VARCHAR(10),
    card_id             BIGINT,
    proofing_checklist  JSONB        DEFAULT '{}',
    metadata            JSONB        DEFAULT '{}',
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    proofing_started_at TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ,
    issued_at           TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    rejected_at         TIMESTAMPTZ,
    expired_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_issuance_tenant_state ON vito.issuance_request (tenant_id, state);
CREATE INDEX idx_issuance_tenant_health ON vito.issuance_request (tenant_id, health_id);
CREATE INDEX idx_issuance_assigned ON vito.issuance_request (assigned_to) WHERE state IN ('SUBMITTED', 'PROOFING');

-- Constraint: only one active issuance per client at a time
CREATE UNIQUE INDEX idx_one_active_issuance ON vito.issuance_request (tenant_id, health_id)
    WHERE state NOT IN ('DELIVERED', 'REJECTED', 'EXPIRED');
