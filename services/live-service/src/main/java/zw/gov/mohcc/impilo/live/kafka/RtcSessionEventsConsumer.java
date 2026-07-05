package zw.gov.mohcc.impilo.live.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.live.core.AttendanceService;
import zw.gov.mohcc.impilo.live.core.LiveEventService;
import zw.gov.mohcc.impilo.live.core.ReplayService;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumes the rtc-gateway media-truth events ({@code impilo.rtc.*}) so what actually
 * happened in the LiveKit room drives live-event workflow state:
 *
 * <ul>
 *   <li>{@code session.started}  → SCHEDULED event goes LIVE (via the existing state machine)</li>
 *   <li>{@code session.finished} → LIVE event goes ENDED</li>
 *   <li>{@code participant.joined/left} → attendance intervals via {@link AttendanceService}
 *       (the attendance system of record; join is a natural-key upsert)</li>
 *   <li>{@code recording.available} → replay pipeline via {@link ReplayService}: egress
 *       artifact adopted into document-service, ENDED → PROCESSING_REPLAY → PUBLISHED_REPLAY</li>
 * </ul>
 *
 * <p>Sessions are resolved by {@code live_event_sessions.provider_room_id = payload.sessionId}:
 * live-service provisions rtc-gateway with its own session UUID and stores the returned room id
 * (the rtc session id) as {@code provider_room_id}. {@code owningRef} (= live event id, stamped
 * at provisioning) is kept as a resolution fallback for sessions the room lookup misses.
 *
 * <p>Only {@code owningService == "LIVE"} events are handled; everything else is ignored
 * silently per the topic contract. Errors are swallowed (logged) so a poison message never
 * kills the listener — the varapi estate consumer convention.
 */
@Component
public class RtcSessionEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(RtcSessionEventsConsumer.class);

    static final String OWNER_LIVE = "LIVE";
    /** Audit actor for media-truth-driven transitions. */
    static final String RTC_ACTOR = "rtc-gateway";
    /** RTC events carry an opaque participant identity, not a vetted participant type. */
    static final String RTC_PARTICIPANT_TYPE = "PARTICIPANT";

    private final ObjectMapper objectMapper;
    private final LiveEventSessionRepository sessionRepository;
    private final LiveEventRepository eventRepository;
    private final LiveEventService eventService;
    private final AttendanceService attendanceService;
    private final ReplayService replayService;

    public RtcSessionEventsConsumer(ObjectMapper objectMapper,
                                    LiveEventSessionRepository sessionRepository,
                                    LiveEventRepository eventRepository,
                                    LiveEventService eventService,
                                    AttendanceService attendanceService,
                                    ReplayService replayService) {
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.replayService = replayService;
    }

    @KafkaListener(
            topics = "${live.rtc.session-started-topic:impilo.rtc.session.started.v1}",
            groupId = "${spring.kafka.consumer.group-id:live-service}")
    public void onSessionStarted(String payload) {
        handle(payload, "session.started", (n, resolved) -> {
            LiveEventEntity event = resolved.event();
            if (LiveEventStatus.SCHEDULED.name().equals(event.getStatus())) {
                eventService.goLive(event.getTenantId(), event.getId(), RTC_ACTOR);
                log.info("RTC session started — live event {} SCHEDULED -> LIVE (rtcSession={})",
                        event.getId(), text(n, "sessionId"));
            } else {
                // Already LIVE (or beyond) — idempotent no-op.
                log.debug("RTC session.started for event {} in status {} — no transition",
                        event.getId(), event.getStatus());
            }
            LiveEventSessionEntity session = resolved.session();
            if (session != null && session.getStartedAt() == null) {
                session.setStartedAt(occurredAt(n));
                session.setHealthStatus("HEALTHY");
                sessionRepository.save(session);
            }
        });
    }

    @KafkaListener(
            topics = "${live.rtc.session-finished-topic:impilo.rtc.session.finished.v1}",
            groupId = "${spring.kafka.consumer.group-id:live-service}")
    public void onSessionFinished(String payload) {
        handle(payload, "session.finished", (n, resolved) -> {
            LiveEventEntity event = resolved.event();
            if (LiveEventStatus.LIVE.name().equals(event.getStatus())) {
                eventService.end(event.getTenantId(), event.getId(), RTC_ACTOR);
                log.info("RTC session finished — live event {} LIVE -> ENDED (rtcSession={})",
                        event.getId(), text(n, "sessionId"));
            } else {
                log.debug("RTC session.finished for event {} in status {} — no transition",
                        event.getId(), event.getStatus());
            }
            LiveEventSessionEntity session = resolved.session();
            if (session != null && session.getEndedAt() == null) {
                session.setEndedAt(occurredAt(n));
                session.setHealthStatus("ENDED");
                sessionRepository.save(session);
            }
            // Recording artifacts can arrive BEFORE the room finishes (facilitator
            // stops recording mid-session): the recording.available handler
            // registers them but cannot publish while the event is LIVE. Run the
            // replay pipeline on end so both arrival orders converge on
            // PUBLISHED_REPLAY. Guarded on a registered artifact so unrecorded
            // classes end honestly in ENDED, not a phantom PROCESSING_REPLAY.
            if (session != null && session.getRecordingRef() != null && !session.getRecordingRef().isBlank()) {
                try {
                    replayService.onSessionEnd(event.getTenantId(), event.getId(), RTC_ACTOR, session);
                } catch (RuntimeException ex) {
                    log.warn("Replay pipeline on session end failed for event {}: {}",
                            event.getId(), ex.getMessage());
                }
            }
        });
    }

    @KafkaListener(
            topics = "${live.rtc.participant-joined-topic:impilo.rtc.participant.joined.v1}",
            groupId = "${spring.kafka.consumer.group-id:live-service}")
    public void onParticipantJoined(String payload) {
        handle(payload, "participant.joined", (n, resolved) -> {
            String identity = text(n, "participantIdentity");
            if (identity == null) {
                log.debug("RTC participant.joined without participantIdentity — skipped");
                return;
            }
            LiveEventEntity event = resolved.event();
            // AttendanceService.join upserts on (eventId, participantId) — a redelivered or
            // rejoin event never double-counts; it reopens the same attendance interval.
            attendanceService.join(event.getTenantId(), event.getId(), identity, RTC_PARTICIPANT_TYPE);
            log.info("RTC participant {} joined live event {}", identity, event.getId());
        });
    }

    @KafkaListener(
            topics = "${live.rtc.participant-left-topic:impilo.rtc.participant.left.v1}",
            groupId = "${spring.kafka.consumer.group-id:live-service}")
    public void onParticipantLeft(String payload) {
        handle(payload, "participant.left", (n, resolved) -> {
            String identity = text(n, "participantIdentity");
            if (identity == null) {
                log.debug("RTC participant.left without participantIdentity — skipped");
                return;
            }
            LiveEventEntity event = resolved.event();
            LiveEventAttendanceEntity attendance;
            try {
                attendance = attendanceService.get(event.getTenantId(), event.getId(), identity);
            } catch (IllegalArgumentException notFound) {
                log.debug("RTC participant.left for {} on event {} without a join record — skipped",
                        identity, event.getId());
                return;
            }
            if (attendance.getLeftAt() != null) {
                // Interval already closed — a duplicate left event must not re-add minutes.
                log.debug("RTC participant.left for {} on event {} already closed — skipped",
                        identity, event.getId());
                return;
            }
            attendanceService.leave(event.getTenantId(), event.getId(), identity);
            log.info("RTC participant {} left live event {}", identity, event.getId());
        });
    }

    /**
     * {@code recording.available} → the egress artifact in MinIO becomes the replay
     * source: {@link ReplayService#onRecordingAvailable} registers it with
     * document-service, stamps {@code recording_ref}, and drives
     * ENDED → PROCESSING_REPLAY → PUBLISHED_REPLAY.
     */
    @KafkaListener(
            topics = "${live.rtc.recording-available-topic:impilo.rtc.recording.available.v1}",
            groupId = "${spring.kafka.consumer.group-id:live-service}")
    public void onRecordingAvailable(String payload) {
        handle(payload, "recording.available", (n, resolved) -> {
            LiveEventSessionEntity session = resolved.session();
            if (session == null) {
                // owningRef fallback resolved the event but not the session row; without a
                // session there is nowhere to stamp recording_ref — skip (redelivery-safe).
                log.warn("RTC recording.available for event {} without a resolvable session row — skipped",
                        resolved.event().getId());
                return;
            }
            replayService.onRecordingAvailable(resolved.event(), session, RTC_ACTOR,
                    new ReplayService.RecordingAvailable(
                            text(n, "egressId"),
                            text(n, "storageBucket"),
                            text(n, "storageKey"),
                            longOrNull(n, "durationSeconds"),
                            longOrNull(n, "sizeBytes")));
            log.info("RTC recording available for session {} (egress={}) — replay pipeline invoked",
                    text(n, "sessionId"), text(n, "egressId"));
        });
    }

    // ── internals ───────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ResolvedHandler {
        void accept(JsonNode payload, Resolved resolved);
    }

    /** The live session row (nullable — resolution may fall back to owningRef) and its event. */
    private record Resolved(LiveEventSessionEntity session, LiveEventEntity event) {}

    private void handle(String payload, String kind, ResolvedHandler handler) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            if (!isLiveOwned(n)) {
                return; // other services' sessions — ignore silently per contract
            }
            Resolved resolved = resolve(n);
            if (resolved == null) {
                log.warn("RTC {} for unknown live session '{}' (owningRef={}) — skipped",
                        kind, text(n, "sessionId"), text(n, "owningRef"));
                return;
            }
            handler.accept(n, resolved);
        } catch (Exception e) {
            // Never poison the listener: log and move on (estate consumer convention).
            log.warn("Failed to handle impilo.rtc {} event: {}", kind, e.getMessage());
        }
    }

    private boolean isLiveOwned(JsonNode n) {
        return OWNER_LIVE.equalsIgnoreCase(text(n, "owningService"));
    }

    /**
     * Primary resolution: {@code provider_room_id = sessionId} (live-service stores the rtc
     * session id there at provisioning). Fallback: {@code owningRef} carries the live event id.
     */
    private Resolved resolve(JsonNode n) {
        String sessionId = text(n, "sessionId");
        if (sessionId != null) {
            LiveEventSessionEntity session = sessionRepository
                    .findFirstByProviderRoomIdOrderByCreatedAtDesc(sessionId)
                    .orElse(null);
            if (session != null) {
                LiveEventEntity event = eventRepository.findById(session.getEventId()).orElse(null);
                if (event != null) {
                    return new Resolved(session, event);
                }
            }
        }
        UUID owningRef = uuidOrNull(text(n, "owningRef"));
        if (owningRef != null) {
            LiveEventEntity event = eventRepository.findById(owningRef).orElse(null);
            if (event != null) {
                return new Resolved(null, event);
            }
        }
        return null;
    }

    private static OffsetDateTime occurredAt(JsonNode n) {
        String raw = text(n, "occurredAt");
        if (raw != null) {
            try {
                return OffsetDateTime.parse(raw);
            } catch (Exception ignored) {
                // fall through to now()
            }
        }
        return OffsetDateTime.now();
    }

    private static UUID uuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asLong();
        }
        try {
            return Long.parseLong(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
