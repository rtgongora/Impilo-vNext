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
 * Citizen support ticket and knowledge base endpoints.
 * GET  /internal/v1/mobile/citizen/support/tickets
 * POST /internal/v1/mobile/citizen/support/tickets
 * GET  /internal/v1/mobile/citizen/support/articles
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/support")
public class CitizenSupportController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenSupportController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreateTicketBody(
            @NotBlank String category,
            @NotBlank String subject,
            @NotBlank String description,
            String priority
    ) {}

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> listTickets(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, category, subject, description, priority, status, resolution, created_at, updated_at
            FROM citizen_support_tickets WHERE tenant_id = ? AND patient_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM citizen_support_tickets WHERE tenant_id = ? AND patient_id = ?",
                Long.class, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toTicketResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tickets")
    @Transactional
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateTicketBody body) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID ticketId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String priority = body.priority() != null ? body.priority() : "NORMAL";

        jdbcTemplate.update("""
            INSERT INTO citizen_support_tickets
                (id, tenant_id, patient_id, category, subject, description, priority, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
            """, ticketId, tenantId, patientId, body.category(), body.subject(),
                body.description(), priority, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.support-ticket-created.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenSupportTicket", ticketId.toString(),
                Map.of("ticket_id", ticketId.toString(), "category", body.category(), "status", "OPEN"),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ticketId.toString());
        data.put("category", body.category());
        data.put("subject", body.subject());
        data.put("description", body.description());
        data.put("priority", priority);
        data.put("status", "OPEN");
        data.put("createdAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> listArticles(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Knowledge articles are empty until populated; return empty set structure
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", size,
                        "total_elements", 0, "total_pages", 0)));
        return ResponseEntity.ok(response);
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }

    private Map<String, Object> toTicketResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("category", row.get("category"));
        r.put("subject", row.get("subject"));
        r.put("description", row.get("description"));
        r.put("priority", row.get("priority"));
        r.put("status", row.get("status"));
        r.put("resolution", row.get("resolution"));
        r.put("createdAt", row.get("created_at"));
        r.put("updatedAt", row.get("updated_at"));
        return r;
    }
}
