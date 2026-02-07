package zw.gov.mohcc.impilo.tshepo.authz.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes token validation to the appropriate SessionAssurance adapter
 * based on the token's issuer claim.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Try each adapter's {@code canHandle(token)} method</li>
 *   <li>First adapter that claims the token validates it</li>
 *   <li>If no adapter claims the token, fall back to Keycloak</li>
 * </ol>
 *
 * <p>This design allows plugging in additional identity providers (e.g.,
 * enterprise SSO) by simply adding another SessionAssurance implementation.</p>
 */
@Service
public class SessionAssuranceRouter {

    private static final Logger log = LoggerFactory.getLogger(SessionAssuranceRouter.class);

    private final List<SessionAssurance> adapters;
    private final KeycloakAdapter keycloakAdapter;

    public SessionAssuranceRouter(List<SessionAssurance> adapters, KeycloakAdapter keycloakAdapter) {
        this.adapters = adapters;
        this.keycloakAdapter = keycloakAdapter;
    }

    /**
     * Validate a bearer token by routing it to the appropriate adapter.
     *
     * @param token the raw bearer token (with or without "Bearer " prefix)
     * @return validated session information
     * @throws SessionValidationException if validation fails
     */
    public SessionInfo validateSession(String token) throws SessionValidationException {
        if (token == null || token.isBlank()) {
            throw new SessionValidationException("NO_TOKEN", "No bearer token provided");
        }

        // Strip "Bearer " prefix if present
        String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (rawToken.isBlank()) {
            throw new SessionValidationException("EMPTY_TOKEN", "Bearer token is empty");
        }

        // Try each adapter
        for (SessionAssurance adapter : adapters) {
            try {
                if (adapter.canHandle(rawToken)) {
                    log.debug("Token routed to adapter: {}", adapter.getClass().getSimpleName());
                    return adapter.validateSession(rawToken);
                }
            } catch (SessionValidationException e) {
                // If the adapter claims it but fails, propagate the error
                throw e;
            } catch (Exception e) {
                log.debug("Adapter {} threw unexpected error for canHandle: {}",
                        adapter.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Fallback to Keycloak
        log.debug("No adapter claimed token, falling back to Keycloak");
        return keycloakAdapter.validateSession(rawToken);
    }
}
