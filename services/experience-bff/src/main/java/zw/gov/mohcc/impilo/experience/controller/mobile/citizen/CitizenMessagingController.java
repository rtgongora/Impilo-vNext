package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen messaging endpoints (conversations, messages).
 * GET  /internal/v1/mobile/citizen/messaging/conversations
 * GET  /internal/v1/mobile/citizen/messaging/conversations/{id}
 * POST /internal/v1/mobile/citizen/messaging/conversations
 * GET  /internal/v1/mobile/citizen/messaging/conversations/{id}/messages
 * POST /internal/v1/mobile/citizen/messaging/conversations/{id}/messages
 * POST /internal/v1/mobile/citizen/messaging/conversations/{id}/read
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/messaging")
public class CitizenMessagingController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenMessagingController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreateConversationBody(
            @NotBlank String recipientId,
            String subject,
            @NotBlank String type,
            @NotBlank String initialMessage
    ) {}

    public record SendMessageBody(
            @NotBlank String body,
            String replyTo
    ) {}

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> listConversations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT c.id, c.subject, c.conversation_type, c.status, c.last_message_at,
                   c.created_at, c.updated_at
            FROM conversations c
            INNER JOIN conversation_participants cp ON cp.conversation_id = c.id
            WHERE c.tenant_id = ? AND cp.participant_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, actorId));

        if (type != null && !type.isBlank()) {
            sql.append(" AND c.conversation_type = ?");
            params.add(type);
        }
        sql.append(" ORDER BY c.last_message_at DESC NULLS LAST LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM conversations c
            INNER JOIN conversation_participants cp ON cp.conversation_id = c.id
            WHERE c.tenant_id = ? AND cp.participant_id = ?
            """, Long.class, tenantId, actorId);

        List<Map<String, Object>> data = rows.stream().map(this::toConvResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, subject, conversation_type, status, last_message_at, created_at, updated_at
            FROM conversations WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Conversation not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toConvResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/conversations")
    @Transactional
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateConversationBody body) {

        UUID convId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO conversations (id, tenant_id, subject, conversation_type, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """, convId, tenantId, body.subject(), body.type(), now, now);

        for (String pid : List.of(actorId, body.recipientId())) {
            jdbcTemplate.update("""
                INSERT INTO conversation_participants (id, conversation_id, participant_id, joined_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), convId, pid, now);
        }

        UUID msgId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO messages (id, tenant_id, conversation_id, sender_id, content, message_type, sent_at, created_at)
            VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?)
            """, msgId, tenantId, convId, actorId, body.initialMessage(), now, now);

        jdbcTemplate.update("UPDATE conversations SET last_message_at = ?, updated_at = ? WHERE id = ?",
                now, now, convId);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.conversation-created.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Conversation", convId.toString(),
                Map.of("conversation_id", convId.toString(), "type", body.type()),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", convId.toString());
        data.put("subject", body.subject());
        data.put("type", body.type());
        data.put("createdAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, conversation_id, sender_id, content, message_type, sent_at, created_at
            FROM messages WHERE conversation_id = ? AND tenant_id = ?
            ORDER BY sent_at ASC LIMIT ? OFFSET ?
            """, id, tenantId, limit, offset);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messages WHERE conversation_id = ? AND tenant_id = ?",
                Long.class, id, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toMsgResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
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
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody SendMessageBody body) {

        UUID msgId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO messages (id, tenant_id, conversation_id, sender_id, content, message_type, sent_at, created_at)
            VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?)
            """, msgId, tenantId, id, actorId, body.body(), now, now);

        jdbcTemplate.update("UPDATE conversations SET last_message_at = ?, updated_at = ? WHERE id = ? AND tenant_id = ?",
                now, now, id, tenantId);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.message-sent.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Message", msgId.toString(),
                Map.of("message_id", msgId.toString(), "conversation_id", id.toString()),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", msgId.toString());
        data.put("conversationId", id.toString());
        data.put("senderId", actorId);
        data.put("body", body.body());
        data.put("sentAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/conversations/{id}/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            UPDATE conversation_participants SET last_read_at = ?
            WHERE conversation_id = ? AND participant_id = ?
            """, now, id, actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("conversationId", id.toString(), "readAt", now));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toConvResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("subject", row.get("subject"));
        r.put("type", row.get("conversation_type"));
        r.put("status", row.get("status"));
        r.put("updatedAt", row.get("updated_at"));
        r.put("createdAt", row.get("created_at"));
        r.put("unreadCount", 0);
        r.put("participants", List.of());
        return r;
    }

    private Map<String, Object> toMsgResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("conversationId", row.get("conversation_id") != null ? row.get("conversation_id").toString() : null);
        r.put("senderId", row.get("sender_id"));
        r.put("senderName", row.get("sender_id"));
        r.put("body", row.get("content"));
        r.put("contentType", row.get("message_type"));
        r.put("sentAt", row.get("sent_at"));
        r.put("status", "SENT");
        r.put("readBy", List.of());
        return r;
    }
}
