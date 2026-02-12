package zw.gov.mohcc.impilo.companion.idempotency;

import java.time.OffsetDateTime;

/**
 * Immutable record representing a stored idempotency key.
 *
 * Composite key: (tenantId, podId, idempotencyKey).
 */
public record IdempotencyRecord(
        String tenantId,
        String podId,
        String idempotencyKey,
        String requestHash,
        int responseStatus,
        String responseBody,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}
