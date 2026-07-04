package zw.gov.mohcc.impilo.rtc.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.RtcOutboxPublisher;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.RtcSessionPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps LiveKit webhook events to Impilo RTC domain events on the transactional
 * outbox, and maintains session lifecycle + participant telemetry columns.
 *
 * <p>Idempotent on the LiveKit event id: a duplicate delivery inserts nothing
 * and publishes nothing.
 */
@Component
public class RtcWebhookTranslator {

    private static final Logger log = LoggerFactory.getLogger(RtcWebhookTranslator.class);

    static final String EVT_SESSION_STARTED = "impilo.rtc.session.started.v1";
    static final String EVT_SESSION_FINISHED = "impilo.rtc.session.finished.v1";
    static final String EVT_PARTICIPANT_JOINED = "impilo.rtc.participant.joined.v1";
    static final String EVT_PARTICIPANT_LEFT = "impilo.rtc.participant.left.v1";
    static final String EVT_TRACK_PUBLISHED = "impilo.rtc.track.published.v1";
    static final String EVT_TRACK_UNPUBLISHED = "impilo.rtc.track.unpublished.v1";
    static final String EVT_RECORDING_STARTED = "impilo.rtc.recording.started.v1";
    static final String EVT_RECORDING_UPDATED = "impilo.rtc.recording.updated.v1";
    static final String EVT_RECORDING_AVAILABLE = "impilo.rtc.recording.available.v1";
    static final String EVT_RECORDING_FAILED = "impilo.rtc.recording.failed.v1";

    private final RtcSessionPersistence sessions;
    private final RtcTelemetryPersistence telemetry;
    private final RtcOutboxPublisher outboxPublisher;

    public RtcWebhookTranslator(RtcSessionPersistence sessions,
                                RtcTelemetryPersistence telemetry,
                                RtcOutboxPublisher outboxPublisher) {
        this.sessions = sessions;
        this.telemetry = telemetry;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * @return outcome for the HTTP layer: PROCESSED, DUPLICATE, UNKNOWN_ROOM, or IGNORED.
     */
    public Outcome handle(JsonNode event) {
        String type = text(event, "event");
        if (type == null) {
            log.warn("LiveKit webhook without an 'event' field — ignored");
            return Outcome.IGNORED;
        }
        String eventId = text(event, "id");
        String roomName = roomName(event);
        String identity = event.path("participant").path("identity").asText(null);
        Instant occurredAt = occurredAt(event);

        RtcSessionRecord session = roomName == null
                ? null
                : sessions.findByRoomName(roomName).orElse(null);

        boolean inserted = telemetry.insertEventIfNew(
                session == null ? null : session.id(),
                eventId, type, identity, roomName, event, occurredAt);
        if (!inserted) {
            log.debug("Duplicate LiveKit webhook {} ({}) — skipped", type, eventId);
            return Outcome.DUPLICATE;
        }
        if (session == null) {
            log.warn("LiveKit webhook {} for unknown room '{}' — recorded raw event only", type, roomName);
            return Outcome.UNKNOWN_ROOM;
        }

        switch (type) {
            case "room_started" -> {
                telemetry.markSessionStarted(session.id(), occurredAt);
                publish(session, eventId, EVT_SESSION_STARTED, occurredAt, identity, null);
            }
            case "room_finished" -> {
                telemetry.markSessionEnded(session.id(), occurredAt);
                publish(session, eventId, EVT_SESSION_FINISHED, occurredAt, identity, null);
            }
            case "participant_joined" -> {
                telemetry.recordParticipantJoined(session.id(), identity, occurredAt, event.path("participant"));
                publish(session, eventId, EVT_PARTICIPANT_JOINED, occurredAt, identity, null);
            }
            case "participant_left" -> {
                String disconnectReason = event.path("participant").path("disconnectReason").asText(null);
                telemetry.recordParticipantLeft(session.id(), identity, occurredAt,
                        disconnectReason, event.path("participant"));
                publish(session, eventId, EVT_PARTICIPANT_LEFT, occurredAt, identity,
                        disconnectReason == null ? null : Map.of("disconnectReason", disconnectReason));
            }
            case "track_published" ->
                    publish(session, eventId, EVT_TRACK_PUBLISHED, occurredAt, identity, trackExtras(event));
            case "track_unpublished" ->
                    publish(session, eventId, EVT_TRACK_UNPUBLISHED, occurredAt, identity, trackExtras(event));
            case "egress_started" ->
                    publish(session, eventId, EVT_RECORDING_STARTED, occurredAt, identity, egressExtras(event));
            case "egress_updated" ->
                    publish(session, eventId, EVT_RECORDING_UPDATED, occurredAt, identity, egressExtras(event));
            case "egress_ended" -> {
                String eventType = egressFailed(event) ? EVT_RECORDING_FAILED : EVT_RECORDING_AVAILABLE;
                publish(session, eventId, eventType, occurredAt, identity, egressExtras(event));
            }
            default -> {
                log.debug("Unmapped LiveKit webhook event '{}' — recorded raw only", type);
                return Outcome.IGNORED;
            }
        }
        return Outcome.PROCESSED;
    }

    private void publish(RtcSessionRecord session, String eventId, String eventType,
                         Instant occurredAt, String participantIdentity, Map<String, Object> extras) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.id());
        payload.put("sessionMode", session.sessionMode());
        payload.put("owningService", session.owningService());
        payload.put("owningRef", session.owningRef());
        payload.put("roomName", session.roomName());
        payload.put("eventId", eventId);
        payload.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        if (participantIdentity != null) {
            payload.put("participantIdentity", participantIdentity);
        }
        if (extras != null) {
            payload.putAll(extras);
        }
        outboxPublisher.append("RtcSession", session.id(), eventType, payload);
    }

    /** Track source (CAMERA/MICROPHONE/SCREEN_SHARE/…) enables screen-share detection downstream. */
    private static Map<String, Object> trackExtras(JsonNode event) {
        Map<String, Object> extras = new LinkedHashMap<>();
        JsonNode track = event.path("track");
        putIfPresent(extras, "source", track.path("source").asText(null));
        putIfPresent(extras, "trackSid", track.path("sid").asText(null));
        putIfPresent(extras, "trackType", track.path("type").asText(null));
        return extras;
    }

    private static Map<String, Object> egressExtras(JsonNode event) {
        Map<String, Object> extras = new LinkedHashMap<>();
        JsonNode egress = event.path("egressInfo");
        putIfPresent(extras, "egressId", egress.path("egressId").asText(null));
        putIfPresent(extras, "egressStatus", egress.path("status").asText(null));
        putIfPresent(extras, "egressError", egress.path("error").asText(null));
        return extras;
    }

    private static boolean egressFailed(JsonNode event) {
        String status = event.path("egressInfo").path("status").asText("");
        String normalized = status.toUpperCase(Locale.ROOT);
        return normalized.contains("FAILED") || normalized.contains("ABORTED");
    }

    private static void putIfPresent(Map<String, Object> extras, String key, String value) {
        if (value != null && !value.isBlank()) {
            extras.put(key, value);
        }
    }

    private static String roomName(JsonNode event) {
        String name = event.path("room").path("name").asText(null);
        if (name == null || name.isBlank()) {
            // Egress events carry the room on egressInfo, not on room.
            name = event.path("egressInfo").path("roomName").asText(null);
        }
        return name == null || name.isBlank() ? null : name;
    }

    private static Instant occurredAt(JsonNode event) {
        JsonNode createdAt = event.path("createdAt");
        if (createdAt.canConvertToLong() && createdAt.asLong() > 0) {
            return Instant.ofEpochSecond(createdAt.asLong());
        }
        return Instant.now();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public enum Outcome {
        PROCESSED, DUPLICATE, UNKNOWN_ROOM, IGNORED
    }
}
