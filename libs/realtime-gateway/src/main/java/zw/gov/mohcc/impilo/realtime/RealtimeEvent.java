package zw.gov.mohcc.impilo.realtime;

import java.util.Map;
import java.util.UUID;

/**
 * A realtime event flowing through the gateway: which tenant + logical channel it targets,
 * its type, the payload, and the originating instance id (so a multi-instance Redis fan-out can
 * avoid re-delivering an event locally that the publishing instance already delivered).
 */
public record RealtimeEvent(
        UUID tenantId,
        String channel,
        String eventType,
        Map<String, Object> payload,
        String originInstanceId
) {}
