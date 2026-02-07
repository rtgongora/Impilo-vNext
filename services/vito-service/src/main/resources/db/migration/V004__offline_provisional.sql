-- VITO — Provisional IDs for offline issuance

CREATE TABLE vito.provisional_id (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    provisional_ref VARCHAR(64)  NOT NULL UNIQUE,
    facility_id     UUID         NOT NULL,
    issued_by       VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, RECONCILED, EXPIRED
    reconciled_to   UUID,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reconciled_at   TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_provisional_status ON vito.provisional_id (tenant_id, status);
