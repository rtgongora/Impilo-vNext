package zw.gov.mohcc.impilo.khuluma.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validates a WebSocket-handshake bearer token for secure browser push (Phase 5 / W7, G-KH-04).
 *
 * <p>Closes the hole where a browser could assert any {@code actorId}/{@code tenantId} via unverified
 * handshake query params. A browser connection must now carry a Tshepo/Keycloak-issued JWT; the actor
 * identity is taken from the <em>verified</em> claims, never from the URL. Decode/signature/expiry
 * failures yield {@code null} → the handshake is rejected.
 */
@Component
public class WsTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(WsTokenValidator.class);

    private final JwtDecoder jwtDecoder;

    public WsTokenValidator(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /** The verified identity of a WS connection. */
    public record WsIdentity(UUID tenantId, String actorId, String actorType) {}

    /**
     * Verify a bearer token and extract the connection identity, or null if the token is absent,
     * malformed, expired, badly signed, or missing the required tenant/actor claims.
     */
    public WsIdentity validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token.trim());
        } catch (Exception e) {
            log.debug("WS token rejected: {}", e.getMessage());
            return null;
        }
        UUID tenantId = parseUuid(firstClaim(jwt, "tenant_id", "X-Tenant-ID"));
        String actorId = firstClaim(jwt, "actor_id", "sub");
        if (tenantId == null || actorId == null || actorId.isBlank()) {
            return null;
        }
        String actorType = firstClaim(jwt, "actor_type");
        return new WsIdentity(tenantId, actorId, actorType != null ? actorType : "PROVIDER");
    }

    private static String firstClaim(Jwt jwt, String... names) {
        for (String n : names) {
            Object v = jwt.getClaim(n);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
