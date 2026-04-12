package zw.gov.mohcc.impilo.experience.controller.mobile;

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
 * Mobile support and escalation endpoints.
 * GET  /internal/v1/mobile/provider/support/tickets?facility_id=               - tickets
 * POST /internal/v1/mobile/provider/support/tickets                            - create ticket
 * GET  /internal/v1/mobile/provider/support/escalations?facility_id=           - escalations
 * POST /internal/v1/mobile/provider/support/escalations/{id}/acknowledge       - acknowledge
 * POST /internal/v1/mobile/provider/support/escalations/{id}/resolve           - resolve
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; target sovereign service is support-service.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/support")
public class MobileSupportController {

    // STRANGLER: JdbcTemplate retained for local reads during migration; target sovereign service is support-service
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileSupportController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (status != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, facility_id, submitted_by, category, subject, description,
                       priority, status, resolved_at, resolution, created_at, updated_at
                FROM support_tickets
                WHERE tenant_id = ? AND facility_id = ?::uuid AND status = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, facilityId, status, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM support_tickets
                WHERE tenant_id = ? AND facility_id = ?::uuid AND status = ?
                """, Long.class, tenantId, facilityId, status);
        } else {
            rows = jdbcTemplate.queryForList("""
                SELECT id, facility_id, submitted_by, category, subject, description,
                       priority, status, resolved_at, resolution, created_at, updated_at
                FROM support_tickets
                WHERE tenant_id = ? AND facility_id = ?::uuid
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, facilityId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM support_tickets WHERE tenant_id = ? AND facility_id = ?::uuid
                """, Long.class, tenantId, facilityId);
        }

        List<Map<String, Object>> data = rows.stream().map(this::toTicketResource).toList();

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

    @PostMapping("/tickets")
    @Transactional
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequest request) {

        UUID ticketId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String priority = request.priority() != null ? request.priority() : "NORMAL";

        jdbcTemplate.update("""
            INSERT INTO support_tickets
                (id, tenant_id, facility_id, submitted_by, category, subject, description,
                 priority, status, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
            """,
                ticketId, tenantId, request.facility_id(), request.submitted_by(),
                request.category(), request.subject(), request.description(),
                priority, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.support_ticket.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "SupportTicket",
                ticketId.toString(),
                Map.of(
                        "ticket_id", ticketId.toString(),
                        "facility_id", request.facility_id(),
                        "category", request.category(),
                        "subject", request.subject(),
                        "priority", priority,
                        "status", "OPEN"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("submitted_by", request.submitted_by());
        attributes.put("category", request.category());
        attributes.put("subject", request.subject());
        attributes.put("description", request.description());
        attributes.put("priority", priority);
        attributes.put("status", "OPEN");
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", ticketId.toString(),
                "type", "SupportTicket",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/escalations")
    public ResponseEntity<Map<String, Object>> listEscalations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, escalated_by, category, subject, description,
                   severity, status, acknowledged_at, acknowledged_by, resolved_at,
                   resolution, notes, created_at, updated_at
            FROM support_escalations
            WHERE tenant_id = ? AND facility_id = ?::uuid
            ORDER BY
                CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                created_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, facilityId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM support_escalations WHERE tenant_id = ? AND facility_id = ?::uuid
            """, Long.class, tenantId, facilityId);

        List<Map<String, Object>> data = rows.stream().map(this::toEscalationResource).toList();

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

    @PostMapping("/escalations/{id}/acknowledge")
    @Transactional
    public ResponseEntity<Map<String, Object>> acknowledgeEscalation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String acknowledgedBy = body != null && body.containsKey("acknowledged_by")
                ? body.get("acknowledged_by").toString()
                : "system";

        int updated = jdbcTemplate.update("""
            UPDATE support_escalations
            SET status = 'ACKNOWLEDGED', acknowledged_at = ?, acknowledged_by = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'OPEN'
            """, now, acknowledgedBy, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Open escalation not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.escalation.acknowledged.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "SupportEscalation",
                id.toString(),
                Map.of(
                        "escalation_id", id.toString(),
                        "status", "ACKNOWLEDGED",
                        "acknowledged_by", acknowledgedBy
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, escalated_by, category, subject, description,
                   severity, status, acknowledged_at, acknowledged_by, resolved_at,
                   resolution, notes, created_at, updated_at
            FROM support_escalations
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toEscalationResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/escalations/{id}/resolve")
    @Transactional
    public ResponseEntity<Map<String, Object>> resolveEscalation(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ResolveEscalationRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE support_escalations
            SET status = 'RESOLVED', resolved_at = ?, resolution = ?,
                notes = COALESCE(?, notes), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('OPEN', 'ACKNOWLEDGED')
            """, now, request.resolution(), request.notes(), now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Resolvable escalation not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.escalation.resolved.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "SupportEscalation",
                id.toString(),
                Map.of(
                        "escalation_id", id.toString(),
                        "status", "RESOLVED",
                        "resolution", request.resolution()
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, escalated_by, category, subject, description,
                   severity, status, acknowledged_at, acknowledged_by, resolved_at,
                   resolution, notes, created_at, updated_at
            FROM support_escalations
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rows.isEmpty() ? Map.of() : toEscalationResource(rows.get(0)));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
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
