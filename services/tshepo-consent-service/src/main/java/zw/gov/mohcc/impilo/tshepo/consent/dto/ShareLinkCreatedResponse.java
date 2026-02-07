package zw.gov.mohcc.impilo.tshepo.consent.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response returned when a share link is created.
 * Contains the plaintext token (returned ONLY once, never stored).
 */
public record ShareLinkCreatedResponse(
        UUID id,
        String token,
        Instant expiresAt,
        int maxUses
) {
}
