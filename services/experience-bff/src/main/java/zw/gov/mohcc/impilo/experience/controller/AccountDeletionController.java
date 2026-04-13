package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

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

    /**
     * Submit an account deletion request.
     */
    @PostMapping("/delete")
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

        UUID requestUuid = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", null);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending deletion request.
     */
    @PostMapping("/delete/cancel")
    public ResponseEntity<Map<String, Object>> cancelDeletion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

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
