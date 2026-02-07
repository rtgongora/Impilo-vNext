package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

import java.time.Instant;

/**
 * Response after a key rotation, confirming the new active key.
 */
public record RotateKeyResponse(
        String oldKeyId,
        String newKeyId,
        String status,
        Instant rotatedAt
) {}
