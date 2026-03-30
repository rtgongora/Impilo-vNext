CREATE TABLE IF NOT EXISTS pct_idempotency_keys (
    tenant_id       TEXT            NOT NULL,
    pod_id          TEXT            NOT NULL,
    idempotency_key TEXT            NOT NULL,
    request_hash    TEXT            NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_pct_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_pct_idempotency_keys_expires
    ON pct_idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;
