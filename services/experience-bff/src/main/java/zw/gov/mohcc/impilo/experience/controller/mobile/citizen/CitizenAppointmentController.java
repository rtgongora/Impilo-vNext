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
 * Citizen appointment endpoints.
 * GET  /internal/v1/mobile/citizen/appointments
 * GET  /internal/v1/mobile/citizen/appointments/{id}
 * POST /internal/v1/mobile/citizen/appointments
 * POST /internal/v1/mobile/citizen/appointments/{id}/cancel
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/appointments")
public class CitizenAppointmentController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenAppointmentController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record RequestAppointmentBody(
            @NotBlank String facilityId,
            @NotBlank String appointmentType,
            @NotBlank String preferredDate,
            String reason
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
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
            SELECT id, facility_id, facility_name, provider_id, provider_name,
                   appointment_type, status, scheduled_at, duration, reason, notes, created_at
            FROM appointments WHERE tenant_id = ? AND patient_id = ?
            """);
        StringBuilder countSql = new StringBuilder(
                "SELECT count(*) FROM appointments WHERE tenant_id = ? AND patient_id = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId, patientId));
        List<Object> cParams = new ArrayList<>(List.of(tenantId, patientId));

        if (status != null) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
            cParams.add(status);
        }
        sql.append(" ORDER BY scheduled_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, cParams.toArray());

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();
        return ResponseEntity.ok(buildPagedResponse(data, requestId, correlationId, page, limit, total));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, facility_name, provider_id, provider_name,
                   appointment_type, status, scheduled_at, duration, reason, notes, created_at
            FROM appointments WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        if (rows.isEmpty()) throw new ResourceNotFoundException("Appointment not found: " + id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody RequestAppointmentBody body) {

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID apptId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime scheduledAt = OffsetDateTime.parse(body.preferredDate() + "T09:00:00Z");

        jdbcTemplate.update("""
            INSERT INTO appointments
                (id, tenant_id, patient_id, facility_id, facility_name, appointment_type,
                 status, scheduled_at, duration, reason, created_at, updated_at)
            VALUES (?, ?, ?, ?::uuid, '', ?, 'SCHEDULED', ?, 30, ?, ?, ?)
            """, apptId, tenantId, patientId, body.facilityId(), body.appointmentType(),
                scheduledAt, body.reason(), now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.appointment-requested.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Appointment", apptId.toString(),
                Map.of("appointment_id", apptId.toString(), "patient_id", patientId.toString(),
                       "type", body.appointmentType(), "status", "SCHEDULED"),
                Map.of());

        Map<String, Object> appt = new LinkedHashMap<>();
        appt.put("id", apptId.toString());
        appt.put("facilityId", body.facilityId());
        appt.put("appointmentType", body.appointmentType());
        appt.put("status", "SCHEDULED");
        appt.put("scheduledAt", scheduledAt);
        appt.put("reason", body.reason());
        appt.put("createdAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", appt);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbcTemplate.update("""
            UPDATE appointments SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'CONFIRMED')
            """, now, id, tenantId);

        if (updated == 0) throw new ResourceNotFoundException("Cancellable appointment not found: " + id);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.appointment-cancelled.v1",
                correlationId, requestId, requestId, tenantId, "citizen-bff",
                "Appointment", id.toString(),
                Map.of("appointment_id", id.toString(), "status", "CANCELLED"),
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

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("facilityId", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        r.put("facilityName", row.get("facility_name"));
        r.put("providerId", row.get("provider_id") != null ? row.get("provider_id").toString() : null);
        r.put("providerName", row.get("provider_name"));
        r.put("appointmentType", row.get("appointment_type"));
        r.put("status", row.get("status"));
        r.put("scheduledAt", row.get("scheduled_at"));
        r.put("duration", row.get("duration"));
        r.put("reason", row.get("reason"));
        r.put("notes", row.get("notes"));
        r.put("createdAt", row.get("created_at"));
        return r;
    }

    private Map<String, Object> buildPagedResponse(List<Map<String, Object>> data, String requestId,
            String correlationId, int page, int size, Long total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page, "size", size,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / size) : 0
                )
        ));
        return response;
    }
}
