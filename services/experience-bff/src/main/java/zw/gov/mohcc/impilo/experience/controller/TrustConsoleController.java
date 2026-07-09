package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.trustconsole.TrustConsoleService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IATG Trust Console BFF routes — unified governance review surface.
 *
 * <p>Mirrors the {@link AdminGovernanceController} conventions: trust headers in, OPA-aligned
 * session precheck + audit inside {@link TrustConsoleService}, and the standard
 * {@code data{type, attributes} / meta{request_id, correlation_id}} envelope out. Per-queue
 * degradation is handled in the service — one dead downstream never 500s the console.</p>
 */
@RestController
@RequestMapping("/internal/v1/trust-console")
public class TrustConsoleController {

    private final TrustConsoleService trustConsoleService;

    public TrustConsoleController(TrustConsoleService trustConsoleService) {
        this.trustConsoleService = trustConsoleService;
    }

    /** Per-queue pending counts (honest partials when a downstream is degraded). */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {
        return ok(requestId, correlationId,
                trustConsoleService.summary(tenantId, actorId, providerId, hasFacility(facilityId)));
    }

    /**
     * One review queue: {@code provider-access-requests | facility-admin-appointments |
     * org-access-requests | assurance-upgrades}.
     */
    @GetMapping("/queues/{queue}")
    public ResponseEntity<Map<String, Object>> queue(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String queue) {
        try {
            return ok(requestId, correlationId, trustConsoleService.queue(
                    tenantId, actorId, providerId, hasFacility(facilityId), queue));
        } catch (IllegalArgumentException e) {
            return badRequest(requestId, correlationId, e.getMessage());
        }
    }

    /** Recently decided items (varapi decisions + governance audit feed). */
    @GetMapping("/decisions/recent")
    public ResponseEntity<Map<String, Object>> recentDecisions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {
        return ok(requestId, correlationId, trustConsoleService.recentDecisions(
                tenantId, actorId, providerId, hasFacility(facilityId)));
    }

    /** Decision passthrough for one queue item ({@code {decision, note}}). */
    @PostMapping("/queues/{queue}/{itemId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @PathVariable String queue,
            @PathVariable String itemId,
            @RequestBody Map<String, Object> body) {
        String decision = str(body.get("decision"));
        if (decision == null) {
            return badRequest(requestId, correlationId, "A decision value is required.");
        }
        try {
            return ok(requestId, correlationId, trustConsoleService.decide(
                    tenantId, actorId, providerId, hasFacility(facilityId),
                    queue, itemId, decision, str(body.get("note"))));
        } catch (IllegalArgumentException e) {
            return badRequest(requestId, correlationId, e.getMessage());
        }
    }

    private static boolean hasFacility(String facilityId) {
        return facilityId != null && !facilityId.isBlank();
    }

    private static String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "type", "trust-console",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static ResponseEntity<Map<String, Object>> badRequest(
            String requestId, String correlationId, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", Map.of("code", "BAD_REQUEST", "message", message != null ? message : "Invalid request"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
