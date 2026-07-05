package zw.gov.mohcc.impilo.khuluma.api.dto;

import zw.gov.mohcc.impilo.khuluma.core.ConversationSummary;
import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallEventEntity;
import zw.gov.mohcc.impilo.khuluma.domain.CallParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageReceiptEntity;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;

import java.util.List;
import java.util.Map;

/**
 * Request/response contracts for the Khuluma internal API ({@code /internal/v1/khuluma/**}),
 * consumed by experience-bff (W1.5). Records are intentionally flat and JSON-snake-case at
 * the wire layer (Spring uses the property names as-is; the BFF maps to its own DTOs).
 */
public final class KhulumaDtos {

    private KhulumaDtos() {}

    // ── requests ──────────────────────────────────────────────────────────────

    public record ParticipantInput(String actorId, String actorType, String displayName, String role) {}

    public record LinkInput(String objectType, String objectId, String linkRole, String note) {}

    public record CreateConversationRequest(String type, String title, String topic,
                                            List<ParticipantInput> participants, List<LinkInput> links) {}

    public record AddParticipantRequest(String actorId, String actorType, String displayName, String role) {}

    public record SendMessageRequest(String body, String contentType, String clientMessageId, String metadata) {}

    public record MarkReadRequest(String messageId) {}

    public record PresenceRequest(String status, String statusMessage, String device) {}

    public record CalleeInput(String actorId, String actorType, String displayName) {}

    public record StartCallRequest(String conversationId, String callType, String displayName,
                                   List<CalleeInput> callees) {}

    public record EndCallRequest(String reason) {}

    public record SignalRequest(String signalType, Map<String, Object> detail) {}

    public record CreateMeetingRequest(String title, List<ParticipantInput> participants, String scheduledAt) {}

    public record MeetingFromEventRequest(String eventId, String title, List<ParticipantInput> participants) {}

    public record LobbyDecisionRequest(String actorId, String reason) {}

    public record CohostRequest(String actorId) {}

    public record RaiseHandRequest(Boolean raised) {}

    public record ReactionRequest(String reaction) {}

    public record ActionItemRequest(String description, String assigneeId, String assigneeDisplayName,
                                    String dueDate, String status) {}

    public record InviteMintRequest(String role, Long ttlSeconds) {}

    public record InviteResolveRequest(String token) {}

    // ── responses ─────────────────────────────────────────────────────────────

    public record ParticipantResponse(String participantId, String actorId, String actorType,
                                       String displayName, String role, boolean active, boolean muted) {
        public static ParticipantResponse of(ConversationParticipantEntity p) {
            return new ParticipantResponse(str(p.getParticipantId()), p.getActorId(), p.getActorType(),
                    p.getDisplayName(), p.getRole(), p.isActive(), p.isMuted());
        }
    }

    public record LinkResponse(String linkId, String objectType, String objectId, String linkRole,
                               String linkedBy, String note) {
        public static LinkResponse of(ConversationLinkEntity l) {
            return new LinkResponse(str(l.getLinkId()), l.getObjectType(), l.getObjectId(),
                    l.getLinkRole(), l.getLinkedBy(), l.getNote());
        }
    }

    public record ConversationResponse(String conversationId, String type, String title, String topic,
                                       String status, String createdBy, String lastMessagePreview,
                                       String lastMessageAt, List<ParticipantResponse> participants,
                                       List<LinkResponse> links) {
        public static ConversationResponse of(ConversationEntity c, List<ConversationParticipantEntity> participants,
                                              List<ConversationLinkEntity> links) {
            return new ConversationResponse(
                    str(c.getConversationId()), c.getConversationType(), c.getTitle(), c.getTopic(),
                    c.getStatus(), c.getCreatedBy(),
                    c.getLastMessagePreview(), c.getLastMessageAt() != null ? c.getLastMessageAt().toString() : null,
                    participants.stream().map(ParticipantResponse::of).toList(),
                    links.stream().map(LinkResponse::of).toList());
        }
    }

    public record ConversationSummaryResponse(String conversationId, String type, String title,
                                              String status, String lastMessagePreview, String lastMessageAt,
                                              String role, boolean muted, long unreadCount) {
        public static ConversationSummaryResponse of(ConversationSummary s) {
            ConversationEntity c = s.conversation();
            return new ConversationSummaryResponse(
                    str(c.getConversationId()), c.getConversationType(), c.getTitle(), c.getStatus(),
                    c.getLastMessagePreview(), c.getLastMessageAt() != null ? c.getLastMessageAt().toString() : null,
                    s.role(), s.muted(), s.unreadCount());
        }
    }

    public record MessageResponse(String messageId, String conversationId, String senderId, String senderType,
                                  String senderDisplayName, String contentType, String body, String status,
                                  String clientMessageId, String sentAt) {
        public static MessageResponse of(MessageEntity m) {
            return new MessageResponse(str(m.getMessageId()), str(m.getConversationId()), m.getSenderId(),
                    m.getSenderType(), m.getSenderDisplayName(), m.getContentType(), m.getBody(), m.getStatus(),
                    m.getClientMessageId(), m.getSentAt() != null ? m.getSentAt().toString() : null);
        }
    }

    public record ReceiptResponse(String actorId, String state, String occurredAt) {
        public static ReceiptResponse of(MessageReceiptEntity r) {
            return new ReceiptResponse(r.getActorId(), r.getReceiptState(),
                    r.getOccurredAt() != null ? r.getOccurredAt().toString() : null);
        }
    }

    public record UnreadCountResponse(long unreadCount, int conversations) {}

    public record PresenceResponse(String actorId, String status, String statusMessage, String lastSeenAt) {
        public static PresenceResponse of(PresenceEntity p) {
            return new PresenceResponse(p.getActorId(), p.getStatus(), p.getStatusMessage(),
                    p.getLastSeenAt() != null ? p.getLastSeenAt().toString() : null);
        }
    }

    public record CallParticipantResponse(String actorId, String actorType, String displayName, String state) {
        public static CallParticipantResponse of(CallParticipantEntity p) {
            return new CallParticipantResponse(p.getActorId(), p.getActorType(), p.getDisplayName(), p.getState());
        }
    }

    public record CallEventResponse(String eventType, String actorId, String occurredAt) {
        public static CallEventResponse of(CallEventEntity e) {
            return new CallEventResponse(e.getEventType(), e.getActorId(),
                    e.getOccurredAt() != null ? e.getOccurredAt().toString() : null);
        }
    }

    public record CallResponse(String callId, String conversationId, String callType, String status,
                               String initiatedBy, String roomName, String roomUrl, String provider,
                               boolean mediaAvailable, String accessToken, String mediaError,
                               List<CallParticipantResponse> participants) {
        public static CallResponse of(CallEntity c, boolean mediaAvailable, String roomUrl, String accessToken,
                                      String mediaError, List<CallParticipantEntity> participants) {
            return new CallResponse(str(c.getCallId()),
                    c.getConversationId() != null ? c.getConversationId().toString() : null,
                    c.getCallType(), c.getStatus(), c.getInitiatedBy(), c.getRoomName(), roomUrl, c.getProvider(),
                    mediaAvailable, accessToken, mediaError,
                    participants.stream().map(CallParticipantResponse::of).toList());
        }
    }

    public record MeetingResponse(String conversationId, String eventId, boolean mediaAvailable,
                                  String roomUrl, String accessToken, String provider, String error) {}

    /** Lobby-aware join outcome: {@code status} READY|WAITING|DENIED|UNAVAILABLE. */
    public record MeetingJoinResponse(String conversationId, String eventId, String status, String role,
                                      String rtcSessionId, boolean mediaAvailable, String roomUrl,
                                      String accessToken, String provider, String error) {}

    public record AdmissionResponse(String actorId, String actorType, String displayName, String role,
                                    String status, String requestedAt, String decidedBy, String decidedAt,
                                    String deniedReason, String joinedAt, String leftAt) {
        public static AdmissionResponse of(zw.gov.mohcc.impilo.khuluma.domain.MeetingAdmissionEntity a) {
            return new AdmissionResponse(a.getActorId(), a.getActorType(), a.getDisplayName(),
                    a.getMeetingRole(), a.getStatus(), str(a.getRequestedAt()), a.getDecidedBy(),
                    str(a.getDecidedAt()), a.getDeniedReason(), str(a.getJoinedAt()), str(a.getLeftAt()));
        }
    }

    public record ActionItemResponse(String actionItemId, String conversationId, String description,
                                     String assigneeId, String assigneeDisplayName, String dueDate,
                                     String status, String createdBy, String createdAt, String updatedAt) {
        public static ActionItemResponse of(zw.gov.mohcc.impilo.khuluma.domain.MeetingActionItemEntity i) {
            return new ActionItemResponse(str(i.getActionItemId()), str(i.getConversationId()),
                    i.getDescription(), i.getAssigneeId(), i.getAssigneeDisplayName(),
                    str(i.getDueDate()), i.getStatus(), i.getCreatedBy(),
                    str(i.getCreatedAt()), str(i.getUpdatedAt()));
        }
    }

    public record InviteResponse(String token, String expiresAt, String role) {}

    public record InviteResolveResponse(String conversationId, String eventId) {}

    public record MeetingDetailResponse(String conversationId, String eventId, String title,
                                        String eventStatus, String scheduledAt, String endTime,
                                        String hostId, List<String> cohostIds,
                                        List<AdmissionResponse> attendance) {}

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
