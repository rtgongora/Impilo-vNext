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
 * runs on its own thread). Identity comes from trust headers (server-to-server / proxied through the
 * BFF) or, as a fallback, handshake query params. A handshake with no resolvable tenant+actor is
 * rejected.
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_TENANT = "khuluma.tenantId";
    static final String ATTR_ACTOR = "khuluma.actorId";
    static final String ATTR_ACTOR_TYPE = "khuluma.actorType";
    static final String ATTR_CHANNELS = "khuluma.channels";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        var http = servletRequest.getServletRequest();

        String tenantRaw = firstNonBlank(http.getHeader("X-Tenant-ID"), http.getParameter("tenantId"));
        String actorId = firstNonBlank(http.getHeader("X-Actor-ID"), http.getParameter("actorId"));
        String actorType = firstNonBlank(http.getHeader("X-Actor-Type"), http.getParameter("actorType"));
        UUID tenantId = parseUuid(tenantRaw);

        if (tenantId == null || actorId == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_TENANT, tenantId);
        attributes.put(ATTR_ACTOR, actorId);
        attributes.put(ATTR_ACTOR_TYPE, actorType != null ? actorType : "PROVIDER");
        attributes.put(ATTR_CHANNELS, http.getParameter("channels"));
        return true;
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
