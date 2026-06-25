package zw.gov.mohcc.impilo.khuluma.core;

import java.util.Map;
import java.util.UUID;

/**
 * Seam for pushing realtime events (message/receipt/presence/ringing/typing) to connected
 * subscribers. W1.2 ships a no-op logging implementation; W1.3 replaces it with the SSE+WS
 * gateway backed by Redis pub/sub for multi-instance fan-out. Core services depend only on
 * this interface so their code is stable across that change.
 */
public interface RealtimeDispatcher {

    /**
     * Publish an event to a logical channel.
     *
     * @param tenantId  owning tenant (fan-out scope)
     * @param channel   logical channel, e.g. {@code "conversation:<id>"} or {@code "actor:<id>"}
     * @param eventType e.g. {@code message.created}, {@code receipt.read}, {@code presence.changed},
     *                  {@code call.ringing}
     * @param payload   serializable event body
     */
    void publish(UUID tenantId, String channel, String eventType, Map<String, Object> payload);
}
