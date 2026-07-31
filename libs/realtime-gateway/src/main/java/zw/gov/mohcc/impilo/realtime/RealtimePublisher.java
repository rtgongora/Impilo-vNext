package zw.gov.mohcc.impilo.realtime;

import java.util.Map;
import java.util.UUID;

/**
 * Seam for pushing realtime events to connected subscribers. Owning services may wrap this with
 * a domain-specific dispatcher interface (e.g. khuluma's {@code RealtimeDispatcher}).
 */
public interface RealtimePublisher {

    void publish(UUID tenantId, String channel, String eventType, Map<String, Object> payload);
}
