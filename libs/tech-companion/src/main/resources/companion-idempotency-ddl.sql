-- Reference DDL for idempotency_keys table.
-- Copy into your service's Flyway migrations and adjust schema prefix.
--
-- Composite PK: (tenant_id, pod_id, idempotency_key)
-- TTL: expires_at should be set to created_at + 24h by the application.
-- Cleanup: run a scheduled DELETE WHERE expires_at < now() or use pg_partman.

CREATE TABLE IF NOT EXISTS idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires ON idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;
