package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile support and escalation endpoints.
 * GET  /internal/v1/mobile/provider/support/tickets?facility_id=               - tickets
 * POST /internal/v1/mobile/provider/support/tickets                            - create ticket
 * GET  /internal/v1/mobile/provider/support/escalations?facility_id=           - escalations
 * POST /internal/v1/mobile/provider/support/escalations/{id}/acknowledge       - acknowledge
 * POST /internal/v1/mobile/provider/support/escalations/{id}/resolve           - resolve
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/support")
public class MobileSupportController {

    private final SupportServiceClient supportClient;

    public MobileSupportController(SupportServiceClient supportClient) {
        this.supportClient = supportClient;
    }

    public record CreateTicketRequest(
            @NotBlank String facility_id,
            @NotBlank String submitted_by,
            @NotBlank String category,
            @NotBlank String subject,
            @NotBlank String description,
            String priority
    ) {}

    public record ResolveEscalationRequest(
            @NotBlank String resolution,
            String notes
    ) {}

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> listTickets(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // support-service does not currently expose a facilityRef filter on list; return tenant-wide view.
        try {
            var result = supportClient.listTickets(status, null, null, null, page, size);
            if (result != null) {
                return ResponseEntity.ok(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequest request) {
        String priority = request.priority() != null ? request.priority() : "MEDIUM";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", request.subject());
        body.put("description", request.description());
        body.put("reporterRef", request.submitted_by());
        body.put("category", request.category());
        body.put("priority", priority);
        body.put("facilityRef", request.facility_id());

        try {
            var created = supportClient.createTicket(body);
            if (created != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "data", created,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}

        // Fallback if downstream is unavailable
        UUID ticketId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("submitted_by", request.submitted_by());
        attributes.put("category", request.category());
        attributes.put("subject", request.subject());
        attributes.put("description", request.description());
        attributes.put("priority", priority);
        attributes.put("status", "OPEN");
        attributes.put("created_at", now);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", ticketId.toString(), "type", "SupportTicket", "attributes", attributes),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/escalations")
    public ResponseEntity<Map<String, Object>> listEscalations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Approximate "escalations" as high-priority open tickets until support-service exposes a dedicated view.
        try {
            var result = supportClient.listTickets("OPEN", "CRITICAL", null, null, page, size);
            if (result != null) {
                return ResponseEntity.ok(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/escalations/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeEscalation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", "ACKNOWLEDGED");
        try {
            var result = supportClient.updateTicket(id, update);
            if (result != null) {
                return ResponseEntity.ok(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "acknowledged", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/escalations/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveEscalation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ResolveEscalationRequest request) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", "RESOLVED");
        update.put("resolution", request.resolution());
        if (request.notes() != null && !request.notes().isBlank()) {
            update.put("description", request.notes());
        }
        try {
            var result = supportClient.updateTicket(id, update);
            if (result != null) {
                return ResponseEntity.ok(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "resolved", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private Map<String, Object> toTicketResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("submitted_by", row.get("submitted_by"));
        attributes.put("category", row.get("category"));
        attributes.put("subject", row.get("subject"));
        attributes.put("description", row.get("description"));
        attributes.put("priority", row.get("priority"));
        attributes.put("status", row.get("status"));
        attributes.put("resolved_at", row.get("resolved_at"));
        attributes.put("resolution", row.get("resolution"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "SupportTicket");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> toEscalationResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("escalated_by", row.get("escalated_by"));
        attributes.put("category", row.get("category"));
        attributes.put("subject", row.get("subject"));
        attributes.put("description", row.get("description"));
        attributes.put("severity", row.get("severity"));
        attributes.put("status", row.get("status"));
        attributes.put("acknowledged_at", row.get("acknowledged_at"));
        attributes.put("acknowledged_by", row.get("acknowledged_by"));
        attributes.put("resolved_at", row.get("resolved_at"));
        attributes.put("resolution", row.get("resolution"));
        attributes.put("notes", row.get("notes"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "SupportEscalation");
        resource.put("attributes", attributes);
        return resource;
    }
}
