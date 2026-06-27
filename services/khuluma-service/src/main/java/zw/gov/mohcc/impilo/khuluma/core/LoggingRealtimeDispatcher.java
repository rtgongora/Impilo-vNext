package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Default no-op {@link RealtimeDispatcher} for W1.2 — logs the event that would be pushed.
 * The W1.3 Redis-backed SSE+WS gateway takes over by registering a {@code @Primary}
 * {@code RealtimeDispatcher} bean.
 */
@Component
public class LoggingRealtimeDispatcher implements RealtimeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingRealtimeDispatcher.class);

    @Override
    public void publish(UUID tenantId, String channel, String eventType, Map<String, Object> payload) {
        log.debug("[realtime:noop] tenant={} channel={} event={} payload={}",
                tenantId, channel, eventType, payload);
    }
}
