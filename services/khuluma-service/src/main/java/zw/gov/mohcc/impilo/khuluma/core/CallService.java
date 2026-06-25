package zw.gov.mohcc.impilo.khuluma.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.client.RtcGatewayClient;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallEventEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.repository.CallEventRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.CallRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationLinkRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the call lifecycle (start → ring → accept/decline → end) layered over rtc-gateway/LiveKit
 * media. Khuluma adds ringing/lifecycle, participant state, signalling log, and escalation links;
 * the media room + tokens come from rtc-gateway. Every transition appends a call event, a domain/
 * audit outbox event, and a realtime push.
 */
@Service
public class CallService {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);
    private static final String AGGREGATE = "Call";

    private final CallRepository calls;
    private final CallParticipantRepository callParticipants;
    private final CallEventRepository callEvents;
    private final ConversationLinkRepository conversationLinks;
    private final RtcGatewayClient rtcGateway;
    private final OutboxAppender outbox;
    private final RealtimeDispatcher realtime;
    private final ObjectMapper objectMapper;

    public CallService(CallRepository calls,
                       CallParticipantRepository callParticipants,
                       CallEventRepository callEvents,
                       ConversationLinkRepository conversationLinks,
                       RtcGatewayClient rtcGateway,
                       OutboxAppender outbox,
                       RealtimeDispatcher realtime,
                       ObjectMapper objectMapper) {
        this.calls = calls;
        this.callParticipants = callParticipants;
        this.callEvents = callEvents;
        this.conversationLinks = conversationLinks;
        this.rtcGateway = rtcGateway;
        this.outbox = outbox;
        this.realtime = realtime;
        this.objectMapper = objectMapper;
    }

    /** A callee to ring when starting a call. */
    public record Callee(String actorId, String actorType, String displayName) {}

    @Transactional
    public CallResult start(ActorContext ctx, UUID conversationId, String callType,
                            String displayName, List<Callee> callees) {
        CallEntity call = new CallEntity();
        call.setTenantId(ctx.tenantId());
        call.setConversationId(conversationId);
        call.setCallType(normalizeType(callType));
        call.setStatus("RINGING");
        call.setInitiatedBy(ctx.actorId());
        call.setInitiatedByType(ctx.actorType());
        calls.save(call);

        // Initiator joins immediately; callees ring.
        upsertParticipant(call, ctx.actorId(), ctx.actorType(), displayName, "JOINED", OffsetDateTime.now());
        if (callees != null) {
            for (Callee c : callees) {
                if (c.actorId() != null && !c.actorId().isBlank() && !c.actorId().equals(ctx.actorId())) {
                    upsertParticipant(call, c.actorId(), c.actorType(), c.displayName(), "RINGING", null);
                }
            }
        }

        // Provision media (best-effort): mint the initiator's token.
        RtcGatewayClient.RtcSessionResult media = rtcGateway.provision(
                ctx.tenantId().toString(), call.getCallId().toString(), patientRef(conversationId, call),
                "PROVIDER".equalsIgnoreCase(ctx.actorType()) ? ctx.actorId() : null,
                "CARE_COORDINATION", call.getCallType(),
                ctx.actorId(), displayName, "HOST");
        if (media.available()) {
            call.setRtcSessionId(media.sessionId());
            call.setRoomName(media.roomName());
            call.setProvider(media.provider());
            calls.save(call);
        }

        recordEvent(call, "RING", ctx.actorId(), Map.of("call_type", call.getCallType()));

        Map<String, Object> payload = callPayload(call);
        outbox.append("impilo.khuluma.call.started.v1", AGGREGATE, call.getCallId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "call-start-" + call.getCallId(), payload,
                call.getCallId().toString(), call.getCallId().toString(), AGGREGATE);

        // Ring each callee on their personal channel.
        if (callees != null) {
            for (Callee c : callees) {
                if (c.actorId() != null && !c.actorId().equals(ctx.actorId())) {
                    realtime.publish(ctx.tenantId(), "actor:" + c.actorId(), "call.ringing", payload);
                }
            }
        }

        log.info("Call started [id={}, type={}, by={}, mediaAvailable={}]",
                call.getCallId(), call.getCallType(), ctx.actorId(), media.available());
        return toResult(call, ctx.actorId(), displayName, media);
    }

    @Transactional
    public CallResult accept(ActorContext ctx, UUID callId, String displayName) {
        CallEntity call = requireCall(ctx, callId);
        CallParticipantEntity participant = requireParticipant(callId, ctx.actorId());
        participant.setState("JOINED");
        participant.setJoinedAt(OffsetDateTime.now());
        callParticipants.save(participant);

        if (!"ACTIVE".equals(call.getStatus())) {
            call.setStatus("ACTIVE");
            if (call.getStartedAt() == null) {
                call.setStartedAt(OffsetDateTime.now());
            }
            calls.save(call);
        }

        // Mint this participant's media token (best-effort).
        RtcGatewayClient.RtcSessionResult media = call.getRtcSessionId() != null
                ? rtcGateway.issueToken(call.getRtcSessionId(), ctx.actorId(), displayName, "PARTICIPANT")
                : RtcGatewayClient.RtcSessionResult.unavailable("no media session provisioned for call");

        recordEvent(call, "ACCEPT", ctx.actorId(), Map.of());
        Map<String, Object> payload = callPayload(call);
        outbox.append("impilo.khuluma.call.accepted.v1", AGGREGATE, callId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "call-accept-" + callId + "-" + ctx.actorId(), payload,
                callId.toString(), callId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "actor:" + call.getInitiatedBy(), "call.accepted", payload);

        return toResult(call, ctx.actorId(), displayName, media);
    }

    @Transactional
    public CallEntity decline(ActorContext ctx, UUID callId) {
        CallEntity call = requireCall(ctx, callId);
        CallParticipantEntity participant = requireParticipant(callId, ctx.actorId());
        participant.setState("DECLINED");
        participant.setLeftAt(OffsetDateTime.now());
        callParticipants.save(participant);

        // If nobody is left ringing or joined besides the initiator, the call is declined.
        boolean anyEngaged = callParticipants.findByCallId(callId).stream()
                .anyMatch(p -> !p.getActorId().equals(call.getInitiatedBy())
                        && ("JOINED".equals(p.getState()) || "RINGING".equals(p.getState())));
        if (!anyEngaged && "RINGING".equals(call.getStatus())) {
            call.setStatus("DECLINED");
            call.setEndReason("DECLINED");
            call.setEndedAt(OffsetDateTime.now());
            calls.save(call);
        }

        recordEvent(call, "DECLINE", ctx.actorId(), Map.of());
        Map<String, Object> payload = callPayload(call);
        outbox.append("impilo.khuluma.call.declined.v1", AGGREGATE, callId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "call-decline-" + callId + "-" + ctx.actorId(), payload,
                callId.toString(), callId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "actor:" + call.getInitiatedBy(), "call.declined", payload);
        return call;
    }

    @Transactional
    public CallEntity end(ActorContext ctx, UUID callId, String reason) {
        CallEntity call = requireCall(ctx, callId);
        call.setStatus("ENDED");
        call.setEndReason(reason != null ? reason : "HANGUP");
        call.setEndedAt(OffsetDateTime.now());
        calls.save(call);

        OffsetDateTime now = OffsetDateTime.now();
        for (CallParticipantEntity p : callParticipants.findByCallId(callId)) {
            if ("JOINED".equals(p.getState()) || "RINGING".equals(p.getState())) {
                p.setState("LEFT");
                p.setLeftAt(now);
                callParticipants.save(p);
            }
        }

        recordEvent(call, "END", ctx.actorId(), Map.of("reason", call.getEndReason()));
        Map<String, Object> payload = callPayload(call);
        outbox.append("impilo.khuluma.call.ended.v1", AGGREGATE, callId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "call-end-" + callId, payload,
                callId.toString(), callId.toString(), AGGREGATE);
        // Notify all participants the call ended.
        for (CallParticipantEntity p : callParticipants.findByCallId(callId)) {
            realtime.publish(ctx.tenantId(), "actor:" + p.getActorId(), "call.ended", payload);
        }
        return call;
    }

    /** Append a signalling event (e.g. typing, custom data-channel passthrough) to a call. */
    @Transactional
    public CallEventEntity signal(ActorContext ctx, UUID callId, String signalType, Map<String, Object> detail) {
        CallEntity call = requireCall(ctx, callId);
        Map<String, Object> body = new LinkedHashMap<>(detail != null ? detail : Map.of());
        body.put("signal_type", signalType);
        CallEventEntity event = recordEvent(call, "SIGNAL", ctx.actorId(), body);
        realtime.publish(ctx.tenantId(), "call:" + callId, "call.signal", body);
        return event;
    }

    @Transactional(readOnly = true)
    public CallEntity get(ActorContext ctx, UUID callId) {
        return requireCall(ctx, callId);
    }

    @Transactional(readOnly = true)
    public List<CallParticipantEntity> participantsOf(UUID callId) {
        return callParticipants.findByCallId(callId);
    }

    @Transactional(readOnly = true)
    public List<CallEventEntity> eventsOf(UUID callId) {
        return callEvents.findByCallIdOrderByOccurredAtAsc(callId);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private CallEntity requireCall(ActorContext ctx, UUID callId) {
        return calls.findByCallIdAndTenantId(callId, ctx.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Call not found: " + callId));
    }

    private CallParticipantEntity requireParticipant(UUID callId, String actorId) {
        return callParticipants.findByCallIdAndActorId(callId, actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor not invited to call: " + actorId));
    }

    private CallParticipantEntity upsertParticipant(CallEntity call, String actorId, String actorType,
                                                    String displayName, String state, OffsetDateTime joinedAt) {
        CallParticipantEntity p = callParticipants.findByCallIdAndActorId(call.getCallId(), actorId)
                .orElseGet(CallParticipantEntity::new);
        p.setCallId(call.getCallId());
        p.setTenantId(call.getTenantId());
        p.setActorId(actorId);
        p.setActorType(actorType != null ? actorType : "PROVIDER");
        p.setDisplayName(displayName);
        p.setState(state);
        if (joinedAt != null) {
            p.setJoinedAt(joinedAt);
        }
        return callParticipants.save(p);
    }

    private CallEventEntity recordEvent(CallEntity call, String eventType, String actorId, Map<String, Object> detail) {
        CallEventEntity event = new CallEventEntity();
        event.setCallId(call.getCallId());
        event.setTenantId(call.getTenantId());
        event.setEventType(eventType);
        event.setActorId(actorId);
        event.setDetailJson(toJson(detail));
        return callEvents.save(event);
    }

    /** A call's clinical subject reference for media provisioning: a linked PATIENT if any, else the call id. */
    private String patientRef(UUID conversationId, CallEntity call) {
        if (conversationId != null) {
            List<zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity> links =
                    conversationLinks.findByConversationId(conversationId);
            for (var link : links) {
                if ("PATIENT".equalsIgnoreCase(link.getObjectType())) {
                    return link.getObjectId();
                }
            }
        }
        return call.getCallId().toString();
    }

    private CallResult toResult(CallEntity call, String actorId, String displayName,
                                RtcGatewayClient.RtcSessionResult media) {
        return new CallResult(call, media.available(), media.roomName(),
                media.roomUrl(), media.accessToken(), media.provider(), media.error());
    }

    private static Map<String, Object> callPayload(CallEntity call) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("call_id", call.getCallId().toString());
        payload.put("conversation_id", call.getConversationId() != null ? call.getConversationId().toString() : null);
        payload.put("call_type", call.getCallType());
        payload.put("status", call.getStatus());
        payload.put("initiated_by", call.getInitiatedBy());
        payload.put("room_name", call.getRoomName());
        return payload;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String normalizeType(String callType) {
        if (callType == null) return "AUDIO";
        return "VIDEO".equalsIgnoreCase(callType.trim()) ? "VIDEO" : "AUDIO";
    }
}
