package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;

import java.util.Map;

/**
 * Backup event-driven comms path — polls booking-service outbox and dispatches appointment comms.
 */
@Component
public class BookingOutboxCommsPoller {

    private static final Logger log = LoggerFactory.getLogger(BookingOutboxCommsPoller.class);
    private static final String CURSOR_KEY = "impilo:booking:outbox:comms:cursor";

    private final BookingServiceClient bookingClient;
    private final AppointmentCommsWorkflowService commsWorkflow;
    private final StringRedisTemplate redis;
    private final boolean enabled;

    public BookingOutboxCommsPoller(
            BookingServiceClient bookingClient,
            AppointmentCommsWorkflowService commsWorkflow,
            StringRedisTemplate redis,
            @Value("${impilo.scheduling.outbox-comms.enabled:true}") boolean enabled) {
        this.bookingClient = bookingClient;
        this.commsWorkflow = commsWorkflow;
        this.redis = redis;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${impilo.scheduling.outbox-comms.poll-interval-ms:30000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            long afterId = readCursor();
            JsonNode response = bookingClient.listOutboxEvents(afterId, 100);
            if (response == null || response.isNull()) {
                return;
            }
            JsonNode rows = response.isArray() ? response : response.path("data");
            if (!rows.isArray() || rows.isEmpty()) {
                return;
            }
            long nextCursor = afterId;
            for (JsonNode row : rows) {
                long eventId = row.path("id").asLong(0);
                if (eventId <= 0) {
                    continue;
                }
                String eventType = row.path("eventType").asText("");
                Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                        .convertValue(row.path("payload"), Map.class);
                if (!commsWorkflow.processOutboxEvent(eventType, payload, eventId)) {
                    // No cross-replica claim could be made, so this event was NOT handled. Stop the
                    // batch here and leave the cursor behind it: advancing past an unhandled event
                    // would drop its comms permanently. The next poll re-offers it from this point.
                    log.warn("Booking outbox comms paused at event {} — dedup unavailable; cursor "
                            + "held at {} and the event will be retried.", eventId, nextCursor);
                    break;
                }
                nextCursor = Math.max(nextCursor, eventId);
            }
            if (nextCursor > afterId) {
                writeCursor(nextCursor);
            }
        } catch (Exception ex) {
            log.warn("Booking outbox comms poller failed: {}", ex.getMessage());
        }
    }

    private long readCursor() {
        try {
            String raw = redis.opsForValue().get(CURSOR_KEY);
            if (raw == null || raw.isBlank()) {
                return 0L;
            }
            return Long.parseLong(raw);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private void writeCursor(long cursor) {
        try {
            redis.opsForValue().set(CURSOR_KEY, Long.toString(cursor));
        } catch (Exception ex) {
            log.debug("Unable to persist booking outbox cursor: {}", ex.getMessage());
        }
    }
}
