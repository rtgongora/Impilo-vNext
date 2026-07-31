package zw.gov.mohcc.impilo.khuluma.realtime;

import zw.gov.mohcc.impilo.realtime.RealtimeEvent;
import zw.gov.mohcc.impilo.realtime.RealtimeHub;
import zw.gov.mohcc.impilo.realtime.RealtimeInstance;
import zw.gov.mohcc.impilo.realtime.RealtimeSubscription;
import zw.gov.mohcc.impilo.realtime.SubscriptionChannelResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * WebSocket transport of the realtime gateway. Shares the subscription/dispatch core
 * ({@link RealtimeHub}) and channel-access rules with the SSE transport, so both deliver the same
 * events. Identity is resolved at handshake by {@link RealtimeHandshakeInterceptor}; this handler
 * registers the session's channel subscription and streams events as JSON text frames.
 */
@Component
public class KhulumaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KhulumaWebSocketHandler.class);
    private static final String ATTR_SUBSCRIPTION = "khuluma.subscriptionId";

    private final SubscriptionChannelResolver channelResolver;
    private final RealtimeHub hub;
    private final ObjectMapper objectMapper;

    public KhulumaWebSocketHandler(SubscriptionChannelResolver channelResolver, RealtimeHub hub,
                                   ObjectMapper objectMapper) {
        this.channelResolver = channelResolver;
        this.hub = hub;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        Map<String, Object> attrs = rawSession.getAttributes();
        UUID tenantId = (UUID) attrs.get(RealtimeHandshakeInterceptor.ATTR_TENANT);
        String actorId = (String) attrs.get(RealtimeHandshakeInterceptor.ATTR_ACTOR);
        String requested = (String) attrs.get(RealtimeHandshakeInterceptor.ATTR_CHANNELS);
        if (tenantId == null || actorId == null) {
            rawSession.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // WebSocketSession is not safe for concurrent sends; decorate it.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 1000, 64 * 1024);
        Set<String> channels = channelResolver.resolveFor(tenantId, actorId, requested);
        String subscriptionId = UUID.randomUUID().toString();
        attrs.put(ATTR_SUBSCRIPTION, subscriptionId);

        RealtimeSubscription.Sink sink = event ->
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(RealtimeStreamController.frame(event))));
        hub.register(new RealtimeSubscription(subscriptionId, tenantId, actorId, channels, sink));

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("event_type", "stream.ready", "actor_id", actorId, "channels", channels))));
        log.debug("WS stream opened [actor={}, channels={}]", actorId, channels.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object subscriptionId = session.getAttributes().get(ATTR_SUBSCRIPTION);
        if (subscriptionId != null) {
            hub.unregister(subscriptionId.toString());
        }
    }
}
