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
 * Citizen marketplace endpoints.
 * GET  /internal/v1/mobile/citizen/marketplace/services
 * GET  /internal/v1/mobile/citizen/marketplace/services/{id}
 * POST /internal/v1/mobile/citizen/marketplace/requests
 * GET  /internal/v1/mobile/citizen/marketplace/requests
 * GET  /internal/v1/mobile/citizen/marketplace/requests/{id}
 * POST /internal/v1/mobile/citizen/marketplace/requests/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/marketplace")
public class CitizenMarketplaceController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenMarketplaceController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record ServiceRequestBody(
            @NotBlank String serviceId,
            String preferredDate,
            String notes
    ) {}

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> listServices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, name, description, category, facility_id, facility_name,
                   price, currency, available, rating, image_url
            FROM marketplace_services WHERE tenant_id = ? AND available = TRUE
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId));

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ?)");
            String searchTerm = "%" + search.toLowerCase() + "%";
            params.add(searchTerm);
            params.add(searchTerm);
        }
        sql.append(" ORDER BY name LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM marketplace_services WHERE tenant_id = ? AND available = TRUE",
                Long.class, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toServiceResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<Map<String, Object>> getService(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, name, description, category, facility_id, facility_name,
                   price, currency, available, rating, image_url
            FROM marketplace_services WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Service not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toServiceResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests")
    @Transactional
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody ServiceRequestBody body) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID reqId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        List<Map<String, Object>> svcRows = jdbcTemplate.queryForList(
                "SELECT name, facility_name FROM marketplace_services WHERE id = ?::uuid AND tenant_id = ?",
                body.serviceId(), tenantId);
        String svcName = svcRows.isEmpty() ? "" : (String) svcRows.get(0).get("name");
        String facName = svcRows.isEmpty() ? "" : (String) svcRows.get(0).get("facility_name");

        jdbcTemplate.update("""
            INSERT INTO service_requests
                (id, tenant_id, patient_id, service_id, service_name, facility_name,
                 status, requested_at, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?::uuid, ?, ?, 'PENDING', ?, ?, ?, ?)
            """, reqId, tenantId, patientId, body.serviceId(), svcName, facName,
                now, body.notes(), now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.service-requested.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "ServiceRequest", reqId.toString(),
                Map.of("request_id", reqId.toString(), "service_id", body.serviceId(), "status", "PENDING"),
                Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", reqId.toString());
        data.put("serviceId", body.serviceId());
        data.put("serviceName", svcName);
        data.put("facilityName", facName);
        data.put("status", "PENDING");
        data.put("requestedAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> listRequests(
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
            SELECT id, service_id, service_name, facility_name, status,
                   requested_at, scheduled_at, completed_at, notes, tracking_number
            FROM service_requests WHERE tenant_id = ? AND patient_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY requested_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM service_requests WHERE tenant_id = ? AND patient_id = ?",
                Long.class, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toRequestResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<Map<String, Object>> getRequest(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, service_id, service_name, facility_name, status,
                   requested_at, scheduled_at, completed_at, notes, tracking_number
            FROM service_requests WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Service request not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toRequestResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelRequest(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbcTemplate.update("""
            UPDATE service_requests SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('PENDING', 'CONFIRMED')
            """, now, id, tenantId);

        if (updated == 0) throw new ResourceNotFoundException("Cancellable service request not found: " + id);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.service-cancelled.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "ServiceRequest", id.toString(),
                Map.of("request_id", id.toString(), "status", "CANCELLED"),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }

    private Map<String, Object> toServiceResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("name", row.get("name"));
        r.put("description", row.get("description"));
        r.put("category", row.get("category"));
        r.put("facilityId", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        r.put("facilityName", row.get("facility_name"));
        r.put("price", row.get("price"));
        r.put("currency", row.get("currency"));
        r.put("available", row.get("available"));
        r.put("rating", row.get("rating"));
        r.put("imageUrl", row.get("image_url"));
        return r;
    }

    private Map<String, Object> toRequestResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("serviceId", row.get("service_id") != null ? row.get("service_id").toString() : null);
        r.put("serviceName", row.get("service_name"));
        r.put("facilityName", row.get("facility_name"));
        r.put("status", row.get("status"));
        r.put("requestedAt", row.get("requested_at"));
        r.put("scheduledAt", row.get("scheduled_at"));
        r.put("completedAt", row.get("completed_at"));
        r.put("notes", row.get("notes"));
        r.put("trackingNumber", row.get("tracking_number"));
        return r;
    }
}
