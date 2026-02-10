-- V016: Idempotency key store for v1.1 POST/PUT/PATCH replay-or-conflict enforcement.
-- Per Manifest v1.1 Companion Spec: repeated same key + same hash => replay; same key + different hash => 409.

CREATE TABLE IF NOT EXISTS vito.idempotency_keys (
    tenant_id       TEXT            NOT NULL,
    pod_id          TEXT            NOT NULL,
    idempotency_key TEXT            NOT NULL,
    request_hash    TEXT            NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NULL,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- Index for expiry-based cleanup
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires
    ON vito.idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;
