package zw.gov.mohcc.impilo.vito.core.idempotency;

import java.time.OffsetDateTime;

/**
 * Immutable representation of a stored idempotency key record.
 * Maps to vito.idempotency_keys table.
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
