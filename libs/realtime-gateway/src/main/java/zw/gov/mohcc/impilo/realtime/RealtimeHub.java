package zw.gov.mohcc.impilo.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single subscription/dispatch core shared by both transports (SSE + WebSocket). Holds the
 * locally-connected subscribers and fans an event out to those whose channel set matches. It is
 * transport-agnostic: SSE emitters and WS sessions both register a {@link RealtimeSubscription}.
 * Cross-instance fan-out is layered on top by a Redis dispatcher, which calls
 * {@link #dispatchLocal} when a remote event arrives.
 *
 * Constructed via {@link RealtimeCoreConfiguration} — not component-scanned.
 */
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private final Map<String, RealtimeSubscription> subscriptions = new ConcurrentHashMap<>();

    public void register(RealtimeSubscription subscription) {
        subscriptions.put(subscription.id(), subscription);
        log.debug("Realtime subscriber registered [id={}, actor={}, channels={}]",
                subscription.id(), subscription.actorId(), subscription.channels());
    }

    public void unregister(String subscriptionId) {
        if (subscriptions.remove(subscriptionId) != null) {
            log.debug("Realtime subscriber unregistered [id={}]", subscriptionId);
        }
    }

    public int subscriberCount() {
        return subscriptions.size();
    }

    public long listenerCount(java.util.UUID tenantId, String channel) {
        return subscriptions.values().stream().filter(s -> s.listensTo(tenantId, channel)).count();
    }

    public void dispatchLocal(RealtimeEvent event) {
        for (RealtimeSubscription sub : subscriptions.values()) {
            if (!sub.listensTo(event.tenantId(), event.channel())) {
                continue;
            }
            try {
                sub.sink().deliver(event);
            } catch (Exception ex) {
                log.debug("Realtime delivery failed, dropping subscriber [id={}]: {}", sub.id(), ex.toString());
                unregister(sub.id());
            }
        }
    }

    public void dispatchLocal(java.util.UUID tenantId, String channel, String eventType,
                              Map<String, Object> payload, String originInstanceId) {
        dispatchLocal(new RealtimeEvent(tenantId, channel, eventType, payload, originInstanceId));
    }
}
