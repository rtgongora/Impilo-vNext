package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataGovernanceServiceClient;

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

    private final DataGovernanceServiceClient dataGovernanceClient;

    public AccountDeletionController(DataGovernanceServiceClient dataGovernanceClient) {
        this.dataGovernanceClient = dataGovernanceClient;
    }

    /**
     * Submit an account deletion request. Proxies to the Data Governance sovereign
     * service (Privacy-by-Architecture §6), which persists the request and emits a
     * domain event for downstream erasure. Idempotent per subject.
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

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("subjectId", userId);
        request.put("requestType", "ACCOUNT_DELETION");
        if (!reason.isBlank()) {
            request.put("reason", reason);
        }

        JsonNode persisted = dataGovernanceClient.submitDataSubjectRequest(
                request, "acctdel-submit-" + userId);

        log.info("Account deletion requested: user={}, requestId={}",
                userId, persisted != null ? persisted.path("requestId").asText("") : "");

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", text(persisted, "status", "PENDING"));
        attributes.put("requestedAt", text(persisted, "requestedAt", null));
        attributes.put("message", "Your account deletion request has been submitted. " +
                "We will process it within a reasonable period.");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", text(persisted, "requestId", userId),
                "type", "account_deletion_request",
                "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Check deletion request status — reflects the real persisted lifecycle state
     * ({@code null} only when no request was ever submitted for this subject).
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

        JsonNode persisted = dataGovernanceClient.getDataSubjectRequestStatus(actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        if (persisted == null) {
            response.put("data", null);
        } else {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("status", text(persisted, "status", null));
            attributes.put("requestedAt", text(persisted, "requestedAt", null));
            attributes.put("updatedAt", text(persisted, "updatedAt", null));
            attributes.put("cancelledAt", text(persisted, "cancelledAt", null));
            attributes.put("completedAt", text(persisted, "completedAt", null));
            response.put("data", Map.of(
                    "id", text(persisted, "requestId", actorId),
                    "type", "account_deletion_request",
                    "attributes", attributes));
        }
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a pending deletion request. Returns 404 when there is no pending request
     * to cancel — the response is no longer a fabricated success.
     */
    @PostMapping("/delete/cancel")
    public ResponseEntity<Map<String, Object>> cancelDeletion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("subjectId", actorId);
        request.put("requestType", "ACCOUNT_DELETION");

        JsonNode cancelled = dataGovernanceClient.cancelDataSubjectRequest(
                request, "acctdel-cancel-" + actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        if (cancelled == null) {
            response.put("error", Map.of("code", "NOT_FOUND",
                    "message", "No pending account deletion request to cancel."));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        response.put("data", Map.of(
                "id", text(cancelled, "requestId", actorId),
                "type", "account_deletion_cancellation",
                "attributes", Map.of(
                        "status", text(cancelled, "status", "CANCELLED"),
                        "message", "Your account deletion request has been cancelled.")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return fallback;
        }
        return node.get(field).asText();
    }
}
