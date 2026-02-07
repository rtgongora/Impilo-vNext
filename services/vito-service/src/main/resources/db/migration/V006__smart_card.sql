-- =============================================================================
-- VITO — SMART Card Lifecycle Management (CMS)
-- State machine: REQUESTED → PRINTED → ACTIVE → INACTIVE → REVOKED
-- Supports secure handover for card replacement.
-- =============================================================================

CREATE TABLE vito.smart_card (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    card_number         VARCHAR(64)  NOT NULL,
    health_id           UUID         NOT NULL REFERENCES vito.client(health_id),
    did_uri             VARCHAR(255) NOT NULL,           -- did:impilo:[hash]
    public_key          TEXT         NOT NULL,           -- PEM-encoded card public key
    status              VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
                                                         -- REQUESTED, PRINTED, ACTIVE,
                                                         -- INACTIVE, REVOKED
    previous_card_id    BIGINT       REFERENCES vito.smart_card(id),
    requested_by        VARCHAR(255) NOT NULL,
    requested_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    printed_at          TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    inactivated_at      TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   VARCHAR(100),                    -- LOST, STOLEN, DAMAGED, EXPIRED, REPLACED
    expires_at          TIMESTAMPTZ  NOT NULL,
    metadata            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_card_number UNIQUE (tenant_id, card_number),
    CONSTRAINT uq_active_card UNIQUE (tenant_id, health_id) WHERE (status = 'ACTIVE')
);

-- Partial unique index: one active card per client per tenant
CREATE UNIQUE INDEX idx_one_active_card
    ON vito.smart_card (tenant_id, health_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_card_health_id ON vito.smart_card (tenant_id, health_id, status);
CREATE INDEX idx_card_did       ON vito.smart_card (did_uri);

-- Card state transition log (immutable audit trail)
CREATE TABLE vito.card_state_transition (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    card_id         BIGINT       NOT NULL REFERENCES vito.smart_card(id),
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)  NOT NULL,
    transitioned_by VARCHAR(255) NOT NULL,
    reason          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_card_transition ON vito.card_state_transition (card_id, created_at);
