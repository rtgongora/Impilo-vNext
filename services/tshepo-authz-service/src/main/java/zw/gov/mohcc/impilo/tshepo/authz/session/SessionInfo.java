package zw.gov.mohcc.impilo.tshepo.authz.session;

import java.util.List;
import java.util.UUID;

/**
 * Validated session information extracted from an OIDC token.
 *
 * <p>This is the canonical representation of a validated session, regardless
 * of whether it came from Keycloak or eSignet. The PolicyEngine uses this
 * to make RBAC/ABAC decisions.</p>
 *
 * @param actorId    The authenticated principal ID (sub claim).
 * @param actorType  Actor type (PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE).
 * @param roles      Realm-level roles from the identity provider.
 * @param tenantId   Tenant ID from the token's organization claim.
 * @param loaLevel   Level of Assurance (0 = none, 1 = basic, 2 = substantial, 3 = high).
 * @param sessionId  Session ID from the token (sid claim).
 * @param issuer     Token issuer URI (identifies which IDP issued the token).
 */
public record SessionInfo(
        String actorId,
        String actorType,
        List<String> roles,
        UUID tenantId,
        int loaLevel,
        String sessionId,
        String issuer
) {}
