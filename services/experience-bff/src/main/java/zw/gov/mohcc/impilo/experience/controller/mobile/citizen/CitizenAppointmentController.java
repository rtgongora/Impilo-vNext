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
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
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
    private final TusoServiceClient tusoClient;

    public CitizenAppointmentController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                        TusoServiceClient tusoClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.tusoClient = tusoClient;
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

        int limit = Math.min(size, 100);

        // STRANGLER: migrated — delegate to TusoServiceClient
        JsonNode appointments = tusoClient.listCitizenAppointments(actorId, status, page, limit);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from appointments table
        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", appointments);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to TusoServiceClient
        JsonNode appointment = tusoClient.getAppointment(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from appointments
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", appointment);
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

        // STRANGLER: migrated — delegate to TusoServiceClient
        Map<String, Object> appointmentData = new LinkedHashMap<>();
        appointmentData.put("facilityId", body.facilityId());
        appointmentData.put("appointmentType", body.appointmentType());
        appointmentData.put("preferredDate", body.preferredDate());
        appointmentData.put("reason", body.reason());

        JsonNode result = tusoClient.createCitizenAppointment(actorId, appointmentData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into appointments table
        // jdbcTemplate.update("""
        //     INSERT INTO appointments (...) VALUES (...)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.appointment-requested.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Appointment", requestId,
                Map.of("type", body.appointmentType(), "status", "SCHEDULED"),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        String reason = body != null && body.containsKey("reason") ? body.get("reason").toString() : "Cancelled";

        // STRANGLER: migrated — delegate to TusoServiceClient
        tusoClient.cancelAppointment(id.toString(), reason);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on appointments
        // jdbcTemplate.update("""
        //     UPDATE appointments SET status = 'CANCELLED', updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'CONFIRMED')
        //     """, ...);

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
}
