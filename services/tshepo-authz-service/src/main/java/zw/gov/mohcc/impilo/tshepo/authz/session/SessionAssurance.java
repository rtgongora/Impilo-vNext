package zw.gov.mohcc.impilo.tshepo.authz.session;

/**
 * Session assurance abstraction layer.
 *
 * <p>Implementations validate bearer tokens against their respective
 * identity providers and extract a canonical {@link SessionInfo}.</p>
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@link KeycloakAdapter} — Keycloak OIDC (primary, for providers/admins)</li>
 *   <li>{@link ESignetAdapter} — eSignet OIDC (optional, for citizens)</li>
 * </ul>
 * </p>
 */
public interface SessionAssurance {

    /**
     * Validate a bearer token and extract session information.
     *
     * @param token the raw bearer token (JWT or opaque)
     * @return validated session info
     * @throws SessionValidationException if the token is invalid, expired, or revoked
     */
    SessionInfo validateSession(String token) throws SessionValidationException;

    /**
     * Check if this adapter can handle the given token.
     *
     * @param token the raw bearer token
     * @return true if this adapter recognizes the token's issuer
     */
    boolean canHandle(String token);
}
