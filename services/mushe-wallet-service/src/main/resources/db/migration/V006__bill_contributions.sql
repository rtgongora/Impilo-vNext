-- Mushe social funding (unified card, Phase 3 feature): a shareable "help pay my bill" link that a
-- person or group can chip in toward. A contribution request has a public share token; each
-- contribution credits the beneficiary's wallet (idempotent) and advances the raised total.
CREATE TABLE mushe.bill_contribution_requests (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    share_token          VARCHAR(48) NOT NULL UNIQUE,
    beneficiary_wallet_id UUID NOT NULL,
    title                VARCHAR(200) NOT NULL,
    bill_ref             VARCHAR(120),
    target_amount        NUMERIC(14,2),
    raised_amount        NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency             VARCHAR(3) NOT NULL DEFAULT 'USD',
    status               VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN | FULFILLED | CLOSED
    created_by           VARCHAR(120),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mushe.bill_contributions (
    id               UUID PRIMARY KEY,
    request_id       UUID NOT NULL REFERENCES mushe.bill_contribution_requests(id),
    amount           NUMERIC(14,2) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'USD',
    contributor_ref  VARCHAR(120),
    contributor_name VARCHAR(160),
    message          VARCHAR(280),
    idempotency_key  VARCHAR(80) UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bill_contributions_request ON mushe.bill_contributions (request_id);
