package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen messaging endpoints (conversations, messages).
 * Delegates to CommunityServiceClient for channels-service operations.
 *
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
    private final CommunityServiceClient communityClient;

    public CitizenMessagingController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                      CommunityServiceClient communityClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.communityClient = communityClient;
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

        // STRANGLER: migrated — delegate to CommunityServiceClient (channels proxy)
        JsonNode conversations = communityClient.listVisits(actorId);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from conversations/conversation_participants tables
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", conversations);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode conversation = communityClient.getUnit(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from conversations
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", conversation);
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

        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> convRequest = new LinkedHashMap<>();
        convRequest.put("recipientId", body.recipientId());
        convRequest.put("subject", body.subject());
        convRequest.put("type", body.type());
        convRequest.put("initialMessage", body.initialMessage());
        convRequest.put("senderId", actorId);

        JsonNode result = communityClient.createUnit(convRequest);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into conversations, conversation_participants, messages tables
        // jdbcTemplate.update("""
        //     INSERT INTO conversations (id, tenant_id, subject, conversation_type, status, created_at, updated_at)
        //     VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.conversation-created.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Conversation", requestId,
                Map.of("type", body.type()),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        // STRANGLER: migrated — delegate to CommunityServiceClient
        JsonNode messages = communityClient.listAssignments(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from messages table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId, limit, offset);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", messages);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
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

        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> msgRequest = new LinkedHashMap<>();
        msgRequest.put("conversationId", id.toString());
        msgRequest.put("senderId", actorId);
        msgRequest.put("body", body.body());
        if (body.replyTo() != null) msgRequest.put("replyTo", body.replyTo());

        JsonNode result = communityClient.createAssignment(msgRequest);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into messages table
        // jdbcTemplate.update("""
        //     INSERT INTO messages (id, tenant_id, conversation_id, sender_id, content, message_type, sent_at, created_at)
        //     VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.message-sent.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Message", requestId,
                Map.of("conversation_id", id.toString()),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        // STRANGLER: migrated — delegate to CommunityServiceClient
        Map<String, Object> readRequest = new LinkedHashMap<>();
        readRequest.put("conversationId", id.toString());
        readRequest.put("participantId", actorId);
        communityClient.startVisit(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on conversation_participants
        // jdbcTemplate.update("""
        //     UPDATE conversation_participants SET last_read_at = ?
        //     WHERE conversation_id = ? AND participant_id = ?
        //     """, now, id, actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("conversationId", id.toString(), "readAt", now));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
