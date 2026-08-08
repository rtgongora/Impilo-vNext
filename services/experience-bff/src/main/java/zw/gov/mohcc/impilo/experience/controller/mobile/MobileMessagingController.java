package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ChannelsServiceClient;

import java.util.*;

/**
 * Mobile messaging endpoints. Every one of these proxies channels-service; none of them
 * may answer on its own behalf when channels-service does not answer.
 *
 * GET  /internal/v1/mobile/provider/messaging/conversations                       - list conversations
 * GET  /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - get messages
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - send message
 * POST /internal/v1/mobile/provider/messaging/conversations                       - create conversation
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/read             - 501, not built
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/messaging")
public class MobileMessagingController {

    private static final Logger log = LoggerFactory.getLogger(MobileMessagingController.class);

    private final ChannelsServiceClient channelsClient;

    public MobileMessagingController(ChannelsServiceClient channelsClient) {
        this.channelsClient = channelsClient;
    }

    public record CreateConversationRequest(
            @NotBlank String subject,
            @NotNull List<String> participant_ids,
            String conversation_type,
            String facility_id
    ) {}

    public record SendMessageRequest(
            @NotBlank String sender_id,
            @NotBlank String content,
            String message_type
    ) {}

    public record MarkReadRequest(
            @NotBlank String participant_id
    ) {}

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> listConversations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "participant_id") String participantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Map "conversations" to channels-service sessions.
        try {
            var sessions = channelsClient.listSessions();
            return ResponseEntity.ok(Map.of(
                    "data", sessions != null ? sessions : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            // An empty 200 here is indistinguishable from a mailbox that genuinely has no
            // conversations, so an upstream failure was being rendered as "you have nothing".
            return upstreamUnavailable("CONVERSATIONS_UNAVAILABLE",
                    "Conversations could not be retrieved. Do not treat this as an empty mailbox.",
                    e, requestId, correlationId);
        }
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            var messages = channelsClient.listMessagesForSession(id);
            return ResponseEntity.ok(Map.of(
                    "data", messages != null ? messages : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            // An empty 200 here reads as "this conversation has no messages" — a different and
            // more dangerous claim than "we could not reach the message store".
            return upstreamUnavailable("MESSAGES_UNAVAILABLE",
                    "Messages could not be retrieved. Do not treat this as an empty conversation.",
                    e, requestId, correlationId);
        }
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SendMessageRequest request) {

        String messageType = request.message_type() != null ? request.message_type() : "TEXT";

        // A 201 is a promise that the message was persisted upstream and will be delivered.
        // Only channels-service can make that promise, so only its answer may produce one:
        // a fabricated id echoing the sender's own content back at them is the worst kind of
        // false success, because the sender stops retrying and the recipient never sees it.
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionId", id.toString());
            body.put("senderRef", request.sender_id());
            body.put("body", request.content());
            body.put("kind", messageType);
            var sent = channelsClient.sendOutboundMessage(body);
            if (sent == null) {
                return upstreamUnavailable("MESSAGE_NOT_SENT",
                        "The message was not sent: the messaging service returned no confirmation. "
                                + "It has NOT been delivered — please try again.",
                        null, requestId, correlationId);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", sent,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            return upstreamUnavailable("MESSAGE_NOT_SENT",
                    "The message was not sent: the messaging service could not be reached. "
                            + "It has NOT been delivered — please try again.",
                    e, requestId, correlationId);
        }
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConversationRequest request) {

        String conversationType = request.conversation_type() != null ? request.conversation_type() : "DIRECT";

        // A fabricated conversation id is worse than a refusal: the client stores it, posts
        // messages into it, and every one of those posts addresses a session that never existed.
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("subject", request.subject());
            body.put("sessionType", conversationType);
            body.put("participantRefs", request.participant_ids());
            if (request.facility_id() != null) body.put("facilityRef", request.facility_id());
            var created = channelsClient.createSession(body);
            if (created == null) {
                return upstreamUnavailable("CONVERSATION_NOT_CREATED",
                        "The conversation was not created: the messaging service returned no confirmation.",
                        null, requestId, correlationId);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            return upstreamUnavailable("CONVERSATION_NOT_CREATED",
                    "The conversation was not created: the messaging service could not be reached.",
                    e, requestId, correlationId);
        }
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MarkReadRequest request) {

        // Read receipts are NOT built. channels-service has no read-state concept at all (no
        // read_at column, no endpoint), and this method never called upstream — it minted a
        // "ConversationReadReceipt" from its own arguments and returned 200. Every unread badge
        // cleared by this call was cleared against nothing. Refuse until the capability exists
        // rather than keep the surface lying.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of(
                        "code", "READ_RECEIPTS_NOT_SUPPORTED",
                        "message", "Marking a conversation read is not supported: the messaging "
                                + "service does not track read state. The conversation has NOT "
                                + "been marked read."),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Upstream could not confirm the operation, so this layer must not confirm it either.
     * {@code 502} says "the answer is unknown/failed" — distinct from an empty success, and
     * distinct from a {@code 404}.
     */
    private ResponseEntity<Map<String, Object>> upstreamUnavailable(
            String code, String message, Exception cause, String requestId, String correlationId) {
        if (cause != null) {
            log.warn("Mobile messaging {}: {}", code, cause.getMessage());
        } else {
            log.warn("Mobile messaging {}: upstream returned no body", code);
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
