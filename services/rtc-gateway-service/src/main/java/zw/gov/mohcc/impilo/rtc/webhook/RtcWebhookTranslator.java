package zw.gov.mohcc.impilo.rtc.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.RtcGatewayProperties;
import zw.gov.mohcc.impilo.rtc.RtcOutboxPublisher;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.RtcParticipantPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcRecordingPersistence;
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
    private final RtcRecordingPersistence recordings;
    private final RtcParticipantPersistence participants;
    private final RtcOutboxPublisher outboxPublisher;
    private final RtcGatewayProperties properties;

    public RtcWebhookTranslator(RtcSessionPersistence sessions,
                                RtcTelemetryPersistence telemetry,
                                RtcRecordingPersistence recordings,
                                RtcParticipantPersistence participants,
                                RtcOutboxPublisher outboxPublisher,
                                RtcGatewayProperties properties) {
        this.sessions = sessions;
        this.telemetry = telemetry;
        this.recordings = recordings;
        this.participants = participants;
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
    }

    /**
     * Resolve the governed session for a LiveKit room, tolerating the
     * provision-commit race: LiveKit (v1.13+) emits room_started at room
     * creation, which can land while the provisioning transaction that
     * inserts the rtc_sessions row is still open. A short bounded retry
     * closes that sub-second visibility window; a genuinely unknown room
     * still falls through to raw-only recording.
     */
    private RtcSessionRecord resolveSession(String roomName) {
        for (int attempt = 0; ; attempt++) {
            RtcSessionRecord session = sessions.findByRoomName(roomName).orElse(null);
            if (session != null || attempt >= 3) {
                return session;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
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

        RtcSessionRecord session = roomName == null ? null : resolveSession(roomName);

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
                // Lobby roster: an admitted participant that actually joined is CONNECTED
                // (no-op when the session mode does not track lobby participants).
                participants.updateState(session.id(), identity, RtcParticipantRecord.STATE_CONNECTED);
                publish(session, eventId, EVT_PARTICIPANT_JOINED, occurredAt, identity, null);
            }
            case "participant_left" -> {
                String disconnectReason = event.path("participant").path("disconnectReason").asText(null);
                telemetry.recordParticipantLeft(session.id(), identity, occurredAt,
                        disconnectReason, event.path("participant"));
                participants.updateState(session.id(), identity, RtcParticipantRecord.STATE_LEFT);
                publish(session, eventId, EVT_PARTICIPANT_LEFT, occurredAt, identity,
                        disconnectReason == null ? null : Map.of("disconnectReason", disconnectReason));
            }
            case "track_published" ->
                    publish(session, eventId, EVT_TRACK_PUBLISHED, occurredAt, identity, trackExtras(event));
            case "track_unpublished" ->
                    publish(session, eventId, EVT_TRACK_UNPUBLISHED, occurredAt, identity, trackExtras(event));
            case "egress_started" -> handleEgressStarted(session, eventId, occurredAt, identity, event);
            case "egress_updated" -> handleEgressUpdated(session, eventId, occurredAt, identity, event);
            case "egress_ended" -> handleEgressEnded(session, eventId, occurredAt, identity, event);
            default -> {
                log.debug("Unmapped LiveKit webhook event '{}' — recorded raw only", type);
                return Outcome.IGNORED;
            }
        }
        return Outcome.PROCESSED;
    }

    // ── Recording lifecycle (rtc.recordings + impilo.rtc.recording.* events) ──
    // The recordings-table update and the enriched event payload live together
    // here so downstream consumers and the recordings API never diverge.

    private void handleEgressStarted(RtcSessionRecord session, String eventId,
                                     Instant occurredAt, String identity, JsonNode event) {
        String egressId = egressId(event);
        if (egressId != null) {
            // Upsert: the webhook can beat the StartRoomCompositeEgress response commit.
            recordings.markActive(session.id(), egressId);
        }
        publish(session, eventId, EVT_RECORDING_STARTED, occurredAt, identity, egressExtras(event));
    }

    private void handleEgressUpdated(RtcSessionRecord session, String eventId,
                                     Instant occurredAt, String identity, JsonNode event) {
        String egressId = egressId(event);
        Long sizeBytes = fileSizeBytes(event);
        Integer durationSeconds = fileDurationSeconds(event);
        if (egressId != null && (sizeBytes != null || durationSeconds != null)) {
            recordings.updateProgress(egressId, sizeBytes, durationSeconds);
        }
        Map<String, Object> extras = egressExtras(event);
        if (sizeBytes != null) {
            extras.put("sizeBytes", sizeBytes);
        }
        if (durationSeconds != null) {
            extras.put("durationSeconds", durationSeconds);
        }
        publish(session, eventId, EVT_RECORDING_UPDATED, occurredAt, identity, extras);
    }

    private void handleEgressEnded(RtcSessionRecord session, String eventId,
                                   Instant occurredAt, String identity, JsonNode event) {
        boolean failed = egressFailed(event);
        String egressId = egressId(event);
        String storageKey = fileStorageKey(event);
        Long sizeBytes = fileSizeBytes(event);
        Integer durationSeconds = fileDurationSeconds(event);
        // The webhook payload carries no bucket; the gateway's configured target is the truth.
        String storageBucket = storageKey == null ? null : properties.getRecording().getS3().getBucket();
        if (egressId != null) {
            recordings.markEnded(session.id(), egressId, failed ? "FAILED" : "COMPLETE",
                    storageBucket, storageKey, sizeBytes, durationSeconds, occurredAt);
        }
        Map<String, Object> extras = egressExtras(event);
        putIfPresent(extras, "storageBucket", storageBucket);
        putIfPresent(extras, "storageKey", storageKey);
        if (durationSeconds != null) {
            extras.put("durationSeconds", durationSeconds);
        }
        if (sizeBytes != null) {
            extras.put("sizeBytes", sizeBytes);
        }
        publish(session, eventId, failed ? EVT_RECORDING_FAILED : EVT_RECORDING_AVAILABLE,
                occurredAt, identity, extras);
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

    private static String egressId(JsonNode event) {
        String id = event.path("egressInfo").path("egressId").asText(null);
        return id == null || id.isBlank() ? null : id;
    }

    /**
     * First file result of the egress, wherever this LiveKit version put it:
     * egressInfo.fileResults[0] (current), egressInfo.file (legacy), or
     * egressInfo.result.file. Missing → MissingNode (all path() calls safe).
     */
    private static JsonNode fileResult(JsonNode event) {
        JsonNode egress = event.path("egressInfo");
        JsonNode results = egress.path("fileResults");
        if (results.isArray() && results.size() > 0) {
            return results.get(0);
        }
        JsonNode file = egress.path("file");
        if (file.isObject()) {
            return file;
        }
        return egress.path("result").path("file");
    }

    private static String fileStorageKey(JsonNode event) {
        JsonNode file = fileResult(event);
        String filename = file.path("filename").asText(null);
        if (filename == null || filename.isBlank()) {
            filename = file.path("filepath").asText(null);
        }
        return filename == null || filename.isBlank() ? null : filename;
    }

    private static Long fileSizeBytes(JsonNode event) {
        long size = fileResult(event).path("size").asLong(0);
        return size > 0 ? size : null;
    }

    /** LiveKit reports duration in nanoseconds (int64, often JSON-encoded as a string). */
    private static Integer fileDurationSeconds(JsonNode event) {
        long duration = fileResult(event).path("duration").asLong(0);
        if (duration <= 0) {
            return null;
        }
        // Defensive: > ~11.5 days in seconds means the value is nanoseconds.
        long seconds = duration > 1_000_000L ? duration / 1_000_000_000L : duration;
        return (int) Math.max(1, seconds);
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
