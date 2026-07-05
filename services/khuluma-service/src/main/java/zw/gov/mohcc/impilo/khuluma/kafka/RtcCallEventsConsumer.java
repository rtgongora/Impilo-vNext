package zw.gov.mohcc.impilo.khuluma.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.CallService;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallEventEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.repository.CallEventRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MeetingAdmissionRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes rtc-gateway media-truth events ({@code impilo.rtc.*}) for Khuluma-owned calls so
 * what actually happened in the media room drives call state:
 *
 * <ul>
 *   <li>{@code participant.joined/left} → {@code khuluma_call_events} audit rows
 *       (RTC_PARTICIPANT_JOINED/LEFT) + participant state JOINED/LEFT</li>
 *   <li>{@code session.finished} → a still-ACTIVE call is ended via {@link CallService#end}
 *       (the existing end path) under a synthetic system {@link ActorContext} — Kafka threads
 *       carry no caller identity</li>
 * </ul>
 *
 * <p>Calls are resolved by {@code khuluma_calls.rtc_session_id = payload.sessionId} (Khuluma
 * provisions rtc-gateway with the call id as the session id and stores the returned session id).
 * {@code owningRef} (also the call id, stamped at provisioning) is the resolution fallback.
 *
 * <p>Only {@code owningService == "KHULUMA"} events are handled; others are ignored silently per
 * the topic contract. Errors are swallowed (logged) so a poison message never kills the listener.
 */
@Component
public class RtcCallEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(RtcCallEventsConsumer.class);

    static final String OWNER_KHULUMA = "KHULUMA";
    /** Synthetic system actor for media-truth-driven transitions on Kafka threads. */
    static final String RTC_ACTOR = "rtc-gateway";
    static final String EVENT_RTC_PARTICIPANT_JOINED = "RTC_PARTICIPANT_JOINED";
    static final String EVENT_RTC_PARTICIPANT_LEFT = "RTC_PARTICIPANT_LEFT";
    static final String END_REASON_RTC_SESSION_FINISHED = "RTC_SESSION_FINISHED";

    private final ObjectMapper objectMapper;
    private final CallRepository calls;
    private final CallParticipantRepository callParticipants;
    private final CallEventRepository callEvents;
    private final CallService callService;
    private final MeetingAdmissionRepository meetingAdmissions;

    public RtcCallEventsConsumer(ObjectMapper objectMapper,
                                 CallRepository calls,
                                 CallParticipantRepository callParticipants,
                                 CallEventRepository callEvents,
                                 CallService callService,
                                 MeetingAdmissionRepository meetingAdmissions) {
        this.objectMapper = objectMapper;
        this.calls = calls;
        this.callParticipants = callParticipants;
        this.callEvents = callEvents;
        this.callService = callService;
        this.meetingAdmissions = meetingAdmissions;
    }

    @KafkaListener(
            topics = "${impilo.khuluma.rtc.session-started-topic:impilo.rtc.session.started.v1}",
            groupId = "${spring.kafka.consumer.group-id:khuluma-service}")
    public void onSessionStarted(String payload) {
        // Khuluma call state is driven by accept (ACTIVE) and participant joins; room start alone
        // carries no additional lifecycle meaning here — observe only.
        try {
            JsonNode n = objectMapper.readTree(payload);
            if (!isKhulumaOwned(n)) {
                return;
            }
            log.debug("RTC session started for khuluma call session {} — no state change", text(n, "sessionId"));
        } catch (Exception e) {
            log.warn("Failed to handle impilo.rtc session.started event: {}", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "${impilo.khuluma.rtc.session-finished-topic:impilo.rtc.session.finished.v1}",
            groupId = "${spring.kafka.consumer.group-id:khuluma-service}")
    public void onSessionFinished(String payload) {
        handle(payload, "session.finished", (n, call) -> {
            if (!"ACTIVE".equals(call.getStatus())) {
                // Already ENDED/DECLINED (or still RINGING — the ring flow owns that outcome).
                log.debug("RTC session.finished for call {} in status {} — no transition",
                        call.getCallId(), call.getStatus());
                return;
            }
            ActorContext ctx = syntheticContext(call, text(n, "eventId"));
            callService.end(ctx, call.getCallId(), END_REASON_RTC_SESSION_FINISHED);
            log.info("RTC session finished — call {} ACTIVE -> ENDED (rtcSession={})",
                    call.getCallId(), text(n, "sessionId"));
        });
    }

    @KafkaListener(
            topics = "${impilo.khuluma.rtc.participant-joined-topic:impilo.rtc.participant.joined.v1}",
            groupId = "${spring.kafka.consumer.group-id:khuluma-service}")
    public void onParticipantJoined(String payload) {
        stampMeetingAttendance(payload, true);
        handle(payload, "participant.joined", (n, call) -> {
            String identity = text(n, "participantIdentity");
            if (identity == null) {
                log.debug("RTC participant.joined without participantIdentity — skipped");
                return;
            }
            Optional<CallParticipantEntity> existing =
                    callParticipants.findByCallIdAndActorId(call.getCallId(), identity);
            if (existing.isPresent() && "JOINED".equals(existing.get().getState())) {
                // Redelivery / duplicate webhook — no state change, no duplicate audit row.
                log.debug("RTC participant.joined for {} on call {} already JOINED — skipped",
                        identity, call.getCallId());
                return;
            }
            existing.ifPresent(p -> {
                p.setState("JOINED");
                if (p.getJoinedAt() == null) {
                    p.setJoinedAt(occurredAt(n));
                }
                callParticipants.save(p);
            });
            appendAuditRow(call, EVENT_RTC_PARTICIPANT_JOINED, identity, payload, occurredAt(n));
            log.info("RTC participant {} joined call {}", identity, call.getCallId());
        });
    }

    @KafkaListener(
            topics = "${impilo.khuluma.rtc.participant-left-topic:impilo.rtc.participant.left.v1}",
            groupId = "${spring.kafka.consumer.group-id:khuluma-service}")
    public void onParticipantLeft(String payload) {
        stampMeetingAttendance(payload, false);
        handle(payload, "participant.left", (n, call) -> {
            String identity = text(n, "participantIdentity");
            if (identity == null) {
                log.debug("RTC participant.left without participantIdentity — skipped");
                return;
            }
            Optional<CallParticipantEntity> existing =
                    callParticipants.findByCallIdAndActorId(call.getCallId(), identity);
            if (existing.isPresent() && "LEFT".equals(existing.get().getState())) {
                log.debug("RTC participant.left for {} on call {} already LEFT — skipped",
                        identity, call.getCallId());
                return;
            }
            existing.ifPresent(p -> {
                p.setState("LEFT");
                p.setLeftAt(occurredAt(n));
                callParticipants.save(p);
            });
            appendAuditRow(call, EVENT_RTC_PARTICIPANT_LEFT, identity, payload, occurredAt(n));
            log.info("RTC participant {} left call {}", identity, call.getCallId());
        });
    }

    /**
     * Meeting attendance proxy: MEETING rooms are provisioned via live-service (owningService
     * LIVE), so the khuluma-owned filter never matches them. Instead, khuluma resolves its own
     * lobby mirror by {@code (rtc_session_id, identity)} and stamps joined/left — the meeting
     * detail endpoint derives attendance from these stamps. No-op for non-meeting sessions.
     */
    private void stampMeetingAttendance(String payload, boolean joined) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            String sessionId = text(n, "sessionId");
            String identity = text(n, "participantIdentity");
            if (sessionId == null || identity == null) {
                return;
            }
            meetingAdmissions.findByRtcSessionIdAndActorId(sessionId, identity).ifPresent(admission -> {
                OffsetDateTime at = occurredAt(n);
                if (joined) {
                    if (admission.getJoinedAt() == null) {
                        admission.setJoinedAt(at);
                    }
                    admission.setLeftAt(null); // reconnect clears the previous leave
                } else {
                    admission.setLeftAt(at);
                }
                admission.setUpdatedAt(OffsetDateTime.now());
                meetingAdmissions.save(admission);
                log.info("Meeting attendance stamped [conversation={}, actor={}, {}]",
                        admission.getConversationId(), identity, joined ? "joined" : "left");
            });
        } catch (Exception e) {
            log.warn("Failed to stamp meeting attendance from impilo.rtc participant event: {}", e.getMessage());
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface CallHandler {
        void accept(JsonNode payload, CallEntity call);
    }

    private void handle(String payload, String kind, CallHandler handler) {
        try {
            JsonNode n = objectMapper.readTree(payload);
            if (!isKhulumaOwned(n)) {
                return; // other services' sessions — ignore silently per contract
            }
            CallEntity call = resolveCall(n);
            if (call == null) {
                log.warn("RTC {} for unknown khuluma call session '{}' (owningRef={}) — skipped",
                        kind, text(n, "sessionId"), text(n, "owningRef"));
                return;
            }
            handler.accept(n, call);
        } catch (Exception e) {
            // Never poison the listener: log and move on (estate consumer convention).
            log.warn("Failed to handle impilo.rtc {} event: {}", kind, e.getMessage());
        }
    }

    private boolean isKhulumaOwned(JsonNode n) {
        return OWNER_KHULUMA.equalsIgnoreCase(text(n, "owningService"));
    }

    /**
     * Primary resolution: {@code rtc_session_id = sessionId}. Fallback: {@code owningRef}
     * carries the call id (stamped by the provisioning client).
     */
    private CallEntity resolveCall(JsonNode n) {
        String sessionId = text(n, "sessionId");
        if (sessionId != null) {
            CallEntity call = calls.findFirstByRtcSessionIdOrderByInitiatedAtDesc(sessionId).orElse(null);
            if (call != null) {
                return call;
            }
        }
        UUID owningRef = uuidOrNull(text(n, "owningRef"));
        if (owningRef != null) {
            return calls.findById(owningRef).orElse(null);
        }
        return null;
    }

    private void appendAuditRow(CallEntity call, String eventType, String actorId,
                                String rawPayload, OffsetDateTime occurredAt) {
        CallEventEntity event = new CallEventEntity();
        event.setCallId(call.getCallId());
        event.setTenantId(call.getTenantId());
        event.setEventType(eventType);
        event.setActorId(actorId);
        event.setDetailJson(rawPayload);
        event.setOccurredAt(occurredAt);
        callEvents.save(event);
    }

    private static ActorContext syntheticContext(CallEntity call, String eventId) {
        return new ActorContext(call.getTenantId(), "national-spine", RTC_ACTOR, "SYSTEM", eventId);
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
}
