package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile messaging endpoints.
 * GET  /internal/v1/mobile/provider/messaging/conversations                       - list conversations
 * GET  /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - get messages
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/messages         - send message
 * POST /internal/v1/mobile/provider/messaging/conversations                       - create conversation
 * POST /internal/v1/mobile/provider/messaging/conversations/{id}/read             - mark read
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; target sovereign service is channels-service.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/messaging")
public class MobileMessagingController {

    // STRANGLER: JdbcTemplate retained for local reads during migration; target sovereign service is channels-service
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileMessagingController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT c.id, c.subject, c.conversation_type, c.facility_id, c.status,
                   c.last_message_at, c.created_at, c.updated_at
            FROM conversations c
            INNER JOIN conversation_participants cp ON cp.conversation_id = c.id
            WHERE c.tenant_id = ? AND cp.participant_id = ?
            ORDER BY c.last_message_at DESC NULLS LAST
            LIMIT ? OFFSET ?
            """, tenantId, participantId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM conversations c
            INNER JOIN conversation_participants cp ON cp.conversation_id = c.id
            WHERE c.tenant_id = ? AND cp.participant_id = ?
            """, Long.class, tenantId, participantId);

        List<Map<String, Object>> data = rows.stream().map(this::toConversationResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, conversation_id, sender_id, content, message_type,
                   sent_at, created_at
            FROM messages
            WHERE conversation_id = ? AND tenant_id = ?
            ORDER BY sent_at ASC
            LIMIT ? OFFSET ?
            """, id, tenantId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM messages WHERE conversation_id = ? AND tenant_id = ?
            """, Long.class, id, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toMessageResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/conversations/{id}/messages")
    @Transactional
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

        jdbcTemplate.update("""
            INSERT INTO messages
                (id, tenant_id, conversation_id, sender_id, content, message_type, sent_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
                messageId, tenantId, id, request.sender_id(), request.content(),
                messageType, now, now);

        jdbcTemplate.update("""
            UPDATE conversations SET last_message_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, now, now, id, tenantId);

        outboxService.writeOutboxEvent(
                "impilo.experience.message.sent.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Message",
                messageId.toString(),
                Map.of(
                        "message_id", messageId.toString(),
                        "conversation_id", id.toString(),
                        "sender_id", request.sender_id(),
                        "message_type", messageType
                ),
                Map.of()
        );

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
    @Transactional
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

        jdbcTemplate.update("""
            INSERT INTO conversations
                (id, tenant_id, subject, conversation_type, facility_id, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::uuid, 'ACTIVE', ?, ?)
            """,
                conversationId, tenantId, request.subject(), conversationType,
                request.facility_id(), now, now);

        for (String participantId : request.participant_ids()) {
            UUID cpId = UUID.randomUUID();
            jdbcTemplate.update("""
                INSERT INTO conversation_participants
                    (id, conversation_id, participant_id, joined_at)
                VALUES (?, ?, ?, ?)
                """, cpId, conversationId, participantId, now);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.conversation.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Conversation",
                conversationId.toString(),
                Map.of(
                        "conversation_id", conversationId.toString(),
                        "subject", request.subject(),
                        "conversation_type", conversationType,
                        "participant_count", String.valueOf(request.participant_ids().size())
                ),
                Map.of()
        );

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
    @Transactional
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody MarkReadRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            UPDATE conversation_participants
            SET last_read_at = ?
            WHERE conversation_id = ? AND participant_id = ?
            """, now, id, request.participant_id());

        outboxService.writeOutboxEvent(
                "impilo.experience.conversation.read.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Conversation",
                id.toString(),
                Map.of(
                        "conversation_id", id.toString(),
                        "participant_id", request.participant_id()
                ),
                Map.of()
        );

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
