package zw.gov.mohcc.impilo.tshepo.contracts.dto;

/**
 * Scoped access token response.
 *
 * <p>Tokens are compact JWS (Ed25519-signed) and carry:
 * tenant, actor, purpose, scope, subject reference, and expiry.
 * They are NOT OIDC tokens — they are internal Impilo service tokens.</p>
 */
public record TokenResponse(
        String token,
        long expiresAt,
        String scope,
        String targetService
) {}
