package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile messaging endpoints.
 * GET  /internal/v1/mobile/provider/messaging/conversations                       - list conversations
 * GET  /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - get messages
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - send message
 * POST /internal/v1/mobile/provider/messaging/conversations                       - create conversation
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/read             - mark read
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/messaging")
public class MobileMessagingController {

    public MobileMessagingController() {
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
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(Map.of("data", List.of()));
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

        UUID messageId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String messageType = request.message_type() != null ? request.message_type() : "TEXT";

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("conversation_id", id.toString());
        attributes.put("sender_id", request.sender_id());
        attributes.put("content", request.content());
        attributes.put("message_type", messageType);
        attributes.put("sent_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", messageId.toString(),
                "type", "Message",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConversationRequest request) {

        UUID conversationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String conversationType = request.conversation_type() != null ? request.conversation_type() : "DIRECT";

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("subject", request.subject());
        attributes.put("conversation_type", conversationType);
        attributes.put("facility_id", request.facility_id());
        attributes.put("status", "ACTIVE");
        attributes.put("participant_ids", request.participant_ids());
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", conversationId.toString(),
                "type", "Conversation",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("conversation_id", id.toString());
        attributes.put("participant_id", request.participant_id());
        attributes.put("last_read_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", id.toString(),
                "type", "ConversationReadReceipt",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toConversationResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("subject", row.get("subject"));
        attributes.put("conversation_type", row.get("conversation_type"));
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("status", row.get("status"));
        attributes.put("last_message_at", row.get("last_message_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Conversation");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> toMessageResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("conversation_id", row.get("conversation_id"));
        attributes.put("sender_id", row.get("sender_id"));
        attributes.put("content", row.get("content"));
        attributes.put("message_type", row.get("message_type"));
        attributes.put("sent_at", row.get("sent_at"));
        attributes.put("created_at", row.get("created_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Message");
        resource.put("attributes", attributes);
        return resource;
    }
}
