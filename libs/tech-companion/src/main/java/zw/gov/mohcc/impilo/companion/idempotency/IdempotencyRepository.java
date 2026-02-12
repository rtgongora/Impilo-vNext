package zw.gov.mohcc.impilo.companion.idempotency;

import java.util.Optional;

/**
 * Repository interface for idempotency key storage.
 *
 * Implementations:
 * - {@link JdbcIdempotencyRepository} — JDBC/JPA hook point (provided)
 * - {@link InMemoryIdempotencyRepository} — for testing
 *
 * Services provide their own table schema via Flyway migration.
 * Reference DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS idempotency_keys (
 *     tenant_id       TEXT NOT NULL,
 *     pod_id          TEXT NOT NULL,
 *     idempotency_key TEXT NOT NULL,
 *     request_hash    TEXT NOT NULL,
 *     response_status INT  NOT NULL,
 *     response_body   TEXT NOT NULL,
 *     created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     expires_at      TIMESTAMPTZ,
 *     PRIMARY KEY (tenant_id, pod_id, idempotency_key)
 * );
 * </pre>
 */
public interface IdempotencyRepository {

    /**
     * Find an existing record by composite key.
     */
    Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey);

    /**
     * Store a new idempotency record.
     * Implementations should handle race conditions gracefully.
     */
    void store(String tenantId, String podId, String idempotencyKey,
               String requestHash, int responseStatus, String responseBody);
}
