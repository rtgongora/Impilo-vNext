package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Routes delegated to channels-service and notification-service via outbox events.
 *
 * POST /internal/v1/mobile/provider/messaging/send           - send message
 * GET  /internal/v1/mobile/provider/messaging/threads        - list message threads
 * GET  /internal/v1/mobile/provider/messaging/threads/{id}   - get thread messages
 * POST /internal/v1/mobile/provider/messaging/notifications/register - register push token
 * GET  /internal/v1/mobile/provider/messaging/notifications  - list notifications
 * POST /internal/v1/mobile/provider/messaging/notifications/read     - mark as read
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/messaging")
public class MobileMessagingController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileMessagingController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record SendMessageRequest(
            @NotBlank String sender_id,
            @NotBlank String recipient_id,
            @NotBlank String channel,
            @NotBlank String content,
            String thread_id,
            String subject,
            String priority
    ) {}

    public record RegisterPushTokenRequest(
            @NotBlank String user_id,
            @NotBlank String device_token,
            @NotBlank String platform
    ) {}

    public record MarkReadRequest(
            @NotBlank List<String> notification_ids
    ) {}

    @PostMapping("/send")
    @Transactional
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody SendMessageRequest request) {

        UUID messageId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String priority = request.priority() != null ? request.priority() : "NORMAL";

        String threadId = request.thread_id();
        if (threadId == null) {
            threadId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                INSERT INTO message_threads
                    (id, tenant_id, subject, participant_ids, last_message_at, created_at, updated_at)
                VALUES (?::uuid, ?, ?, ARRAY[?, ?], ?, ?, ?)
                """,
                    threadId, tenantId, request.subject(),
                    request.sender_id(), request.recipient_id(),
                    now, now, now);
        } else {
            jdbcTemplate.update("""
                UPDATE message_threads SET last_message_at = ?, updated_at = ?
                WHERE id = ?::uuid AND tenant_id = ?
                """, now, now, threadId, tenantId);
        }

        jdbcTemplate.update("""
            INSERT INTO messages
                (id, tenant_id, thread_id, sender_id, recipient_id, channel, content,
                 priority, status, sent_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, 'SENT', ?, ?, ?)
            """,
                messageId, tenantId, threadId, request.sender_id(), request.recipient_id(),
                request.channel(), request.content(), priority,
                now, now, now);

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
                        "thread_id", threadId,
                        "sender_id", request.sender_id(),
                        "recipient_id", request.recipient_id(),
                        "channel", request.channel(),
                        "priority", priority
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("thread_id", threadId);
        attributes.put("sender_id", request.sender_id());
        attributes.put("recipient_id", request.recipient_id());
        attributes.put("channel", request.channel());
        attributes.put("content", request.content());
        attributes.put("priority", priority);
        attributes.put("status", "SENT");
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

    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> listThreads(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "user_id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT t.id, t.subject, t.participant_ids, t.last_message_at, t.created_at, t.updated_at,
                   (SELECT count(*) FROM messages m WHERE m.thread_id = t.id
                    AND m.recipient_id = ? AND m.read_at IS NULL) AS unread_count,
                   (SELECT m.content FROM messages m WHERE m.thread_id = t.id
                    ORDER BY m.sent_at DESC LIMIT 1) AS last_message
            FROM message_threads t
            WHERE t.tenant_id = ? AND ? = ANY(t.participant_ids)
            ORDER BY t.last_message_at DESC
            LIMIT ? OFFSET ?
            """, userId, tenantId, userId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM message_threads
            WHERE tenant_id = ? AND ? = ANY(participant_ids)
            """, Long.class, tenantId, userId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("subject", row.get("subject"));
            attributes.put("participant_ids", row.get("participant_ids"));
            attributes.put("last_message", row.get("last_message"));
            attributes.put("unread_count", row.get("unread_count"));
            attributes.put("last_message_at", row.get("last_message_at"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "MessageThread");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

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

    @GetMapping("/threads/{id}")
    public ResponseEntity<Map<String, Object>> getThreadMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, thread_id, sender_id, recipient_id, channel, content,
                   priority, status, sent_at, read_at, created_at, updated_at
            FROM messages
            WHERE thread_id = ? AND tenant_id = ?
            ORDER BY sent_at ASC
            LIMIT ? OFFSET ?
            """, id, tenantId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM messages WHERE thread_id = ? AND tenant_id = ?
            """, Long.class, id, tenantId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("thread_id", row.get("thread_id"));
            attributes.put("sender_id", row.get("sender_id"));
            attributes.put("recipient_id", row.get("recipient_id"));
            attributes.put("channel", row.get("channel"));
            attributes.put("content", row.get("content"));
            attributes.put("priority", row.get("priority"));
            attributes.put("status", row.get("status"));
            attributes.put("sent_at", row.get("sent_at"));
            attributes.put("read_at", row.get("read_at"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Message");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

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

    @PostMapping("/notifications/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> registerPushToken(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RegisterPushTokenRequest request) {

        UUID tokenId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO push_tokens
                (id, tenant_id, user_id, device_token, platform, status, registered_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
            ON CONFLICT (tenant_id, user_id, device_token)
            DO UPDATE SET platform = EXCLUDED.platform, status = 'ACTIVE', updated_at = EXCLUDED.updated_at
            """,
                tokenId, tenantId, request.user_id(), request.device_token(),
                request.platform(), now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.push_token.registered.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "PushToken",
                tokenId.toString(),
                Map.of(
                        "user_id", request.user_id(),
                        "platform", request.platform(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("user_id", request.user_id());
        attributes.put("platform", request.platform());
        attributes.put("status", "ACTIVE");
        attributes.put("registered_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", tokenId.toString(),
                "type", "PushToken",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> listNotifications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "user_id") String userId,
            @RequestParam(required = false, name = "unread_only") Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (Boolean.TRUE.equals(unreadOnly)) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, user_id, title, body, category, action_url, read_at, created_at
                FROM notifications
                WHERE tenant_id = ? AND user_id = ? AND read_at IS NULL
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, userId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM notifications
                WHERE tenant_id = ? AND user_id = ? AND read_at IS NULL
                """, Long.class, tenantId, userId);
        } else {
            rows = jdbcTemplate.queryForList("""
                SELECT id, user_id, title, body, category, action_url, read_at, created_at
                FROM notifications
                WHERE tenant_id = ? AND user_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, userId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM notifications WHERE tenant_id = ? AND user_id = ?
                """, Long.class, tenantId, userId);
        }

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("user_id", row.get("user_id"));
            attributes.put("title", row.get("title"));
            attributes.put("body", row.get("body"));
            attributes.put("category", row.get("category"));
            attributes.put("action_url", row.get("action_url"));
            attributes.put("read_at", row.get("read_at"));
            attributes.put("created_at", row.get("created_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Notification");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

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

    @PostMapping("/notifications/read")
    @Transactional
    public ResponseEntity<Map<String, Object>> markNotificationsRead(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody MarkReadRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        int totalMarked = 0;

        for (String notificationId : request.notification_ids()) {
            int updated = jdbcTemplate.update("""
                UPDATE notifications SET read_at = ?, updated_at = ?
                WHERE id = ?::uuid AND tenant_id = ? AND read_at IS NULL
                """, now, now, notificationId, tenantId);
            totalMarked += updated;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "marked_read", totalMarked,
                "requested", request.notification_ids().size()
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
