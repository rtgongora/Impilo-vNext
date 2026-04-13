package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Account deletion request endpoints.
 *
 * <p>Per Privacy Policy §13: Users may request and initiate deletion of
 * their Impilo account directly within Impilo vNext.</p>
 *
 * <p>Deletion is a two-phase process:
 * <ol>
 *   <li>User submits a deletion request (this controller records it).</li>
 *   <li>Backend processes the request asynchronously — deactivates Keycloak
 *       account, anonymizes PII in VITO, revokes consents, and publishes
 *       audit events.</li>
 * </ol>
 * Some data may be retained where required by law (security, audit, fraud
 * prevention, public health, continuity of care, recordkeeping).</p>
 */
@RestController
@RequestMapping("/internal/v1/account")
public class AccountDeletionController {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionController.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public AccountDeletionController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    /**
     * Submit an account deletion request.
     */
    @PostMapping("/delete")
    @Transactional
    public ResponseEntity<Map<String, Object>> requestDeletion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String userId = actorId != null && !actorId.isBlank() ? actorId : body.getOrDefault("userId", "").toString();
        String reason = body.getOrDefault("reason", "").toString();

        if (userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "userId is required")));
        }

        // Check for existing pending request
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_deletion_request " +
                "WHERE tenant_id = ? AND user_id = ? AND status IN ('PENDING', 'PROCESSING')",
                Integer.class, tenantId, userId);

        if (existing != null && existing > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", Map.of("code", "DELETION_PENDING",
                            "message", "An account deletion request is already pending.")));
        }

        UUID requestUuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO account_deletion_request (id, tenant_id, user_id, status, reason, " +
                "requested_at, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', ?, ?, NOW(), NOW())",
                requestUuid, tenantId, userId, reason.isBlank() ? null : reason, now);

        // Revoke all active consents
        jdbcTemplate.update(
                "UPDATE policy_consent SET accepted = FALSE, revoked_at = ?, updated_at = NOW() " +
                "WHERE tenant_id = ? AND user_id = ? AND accepted = TRUE",
                now, tenantId, userId);

        // Publish deletion request event for async processing
        outboxService.writeOutboxEvent(
                "impilo.experience.account.deletion-requested.v1",
                correlationId,
                requestId,
                requestUuid.toString(),
                tenantId,
                podId,
                "AccountDeletion",
                userId,
                Map.of(
                        "requestId", requestUuid.toString(),
                        "userId", userId,
                        "tenantId", tenantId,
                        "reason", reason,
                        "requestedAt", now.toString()
                ),
                Map.of());

        log.info("Account deletion requested: user={}, requestId={}", userId, requestUuid);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", requestUuid.toString(),
                "type", "account_deletion_request",
                "attributes", Map.of(
                        "status", "PENDING",
                        "requestedAt", now.toString(),
                        "message", "Your account deletion request has been submitted. " +
                                   "We will process it within a reasonable period."
                )
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Check deletion request status.
     */
    @GetMapping("/delete/status")
    public ResponseEntity<Map<String, Object>> getDeletionStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        List<Map<String, Object>> requests = jdbcTemplate.queryForList(
                "SELECT id, status, reason, requested_at, processed_at, completed_at " +
                "FROM account_deletion_request WHERE tenant_id = ? AND user_id = ? " +
                "ORDER BY requested_at DESC LIMIT 1",
                tenantId, actorId);

        if (requests.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", null);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }

        Map<String, Object> r = requests.get(0);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", r.get("id").toString(),
                "type", "account_deletion_request",
                "attributes", Map.of(
                        "status", r.get("status"),
                        "requestedAt", r.get("requested_at").toString(),
                        "processedAt", r.get("processed_at") != null ? r.get("processed_at").toString() : "",
                        "completedAt", r.get("completed_at") != null ? r.get("completed_at").toString() : ""
                )
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending deletion request.
     */
    @PostMapping("/delete/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelDeletion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        int updated = jdbcTemplate.update(
                "UPDATE account_deletion_request SET status = 'CANCELLED', updated_at = NOW() " +
                "WHERE tenant_id = ? AND user_id = ? AND status = 'PENDING'",
                tenantId, actorId);

        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND",
                            "message", "No pending deletion request found.")));
        }

        log.info("Account deletion cancelled: user={}", actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", "cancelled",
                "type", "account_deletion_cancellation",
                "attributes", Map.of(
                        "status", "CANCELLED",
                        "message", "Your account deletion request has been cancelled."
                )
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
