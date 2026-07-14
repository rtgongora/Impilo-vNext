-- idempotency_keys is NOT a JPA entity; in production Flyway creates it.
-- The H2 test profile disables Flyway, so the tech-companion JDBC
-- idempotency repository needs the table created here.
CREATE TABLE IF NOT EXISTS tshepo.idempotency_keys (
    tenant_id       VARCHAR(255) NOT NULL,
    pod_id          VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(512) NOT NULL,
    response_status INT          NOT NULL,
    response_body   CLOB         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NULL,
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
