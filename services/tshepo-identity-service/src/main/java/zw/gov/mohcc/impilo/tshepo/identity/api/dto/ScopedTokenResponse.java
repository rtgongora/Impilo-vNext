package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;

/**
 * Response carrying an issued scoped token.
 */
public record ScopedTokenResponse(
        String token,
        String jti,
        String scope,
        String targetService,
        Instant expiresAt
) {}
