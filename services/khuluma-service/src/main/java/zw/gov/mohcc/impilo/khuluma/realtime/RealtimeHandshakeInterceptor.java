package zw.gov.mohcc.impilo.khuluma.realtime;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves the connecting actor's trust identity at WebSocket-handshake time and stashes it in the
 * session attributes (the {@code TrustContextHolder} thread-local is cleared before the WS handler
 * runs on its own thread).
 *
 * <p>Secure browser push (W7, G-KH-04): identity comes from Envoy-validated trust headers
 * (server-to-server / BFF-proxied) OR a <em>verified</em> JWT (handshake {@code Authorization: Bearer}
 * or {@code ?access_token=}). Unverified {@code actorId}/{@code tenantId} query params are NO LONGER
 * trusted — that was an identity-spoofing hole for browser connections. A handshake that resolves to
 * neither a trusted header identity nor a valid token is rejected with 401.
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_TENANT = "khuluma.tenantId";
    static final String ATTR_ACTOR = "khuluma.actorId";
    static final String ATTR_ACTOR_TYPE = "khuluma.actorType";
    static final String ATTR_CHANNELS = "khuluma.channels";

    private final WsTokenValidator tokenValidator;

    public RealtimeHandshakeInterceptor(WsTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        var http = servletRequest.getServletRequest();

        // 1. Server-to-server / BFF-proxied: trust headers (already validated by Envoy ext_authz).
        UUID tenantId = parseUuid(http.getHeader("X-Tenant-ID"));
        String actorId = blankToNull(http.getHeader("X-Actor-ID"));
        String actorType = blankToNull(http.getHeader("X-Actor-Type"));

        // 2. Browser connection: require a verified JWT. Identity comes from the token claims only.
        if (tenantId == null || actorId == null) {
            WsTokenValidator.WsIdentity id = tokenValidator.validate(bearerToken(http));
            if (id == null) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            tenantId = id.tenantId();
            actorId = id.actorId();
            actorType = id.actorType();
        }

        if (tenantId == null || actorId == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_TENANT, tenantId);
        attributes.put(ATTR_ACTOR, actorId);
        attributes.put(ATTR_ACTOR_TYPE, actorType != null ? actorType : "PROVIDER");
        attributes.put(ATTR_CHANNELS, http.getParameter("channels"));
        return true;
    }

    /** Bearer token from the Authorization header or, for browsers (no WS custom headers), ?access_token=. */
    private static String bearerToken(jakarta.servlet.http.HttpServletRequest http) {
        String auth = http.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return firstNonBlank(http.getParameter("access_token"), http.getParameter("token"));
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
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
