package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

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

    private final TusoServiceClient tusoClient;

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

        JsonNode appointments = tusoClient.listCitizenAppointments(actorId, status, page, limit);

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

        JsonNode appointment = tusoClient.getAppointment(id.toString());

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

        Map<String, Object> appointmentData = new LinkedHashMap<>();
        appointmentData.put("facilityId", body.facilityId());
        appointmentData.put("appointmentType", body.appointmentType());
        appointmentData.put("preferredDate", body.preferredDate());
        appointmentData.put("reason", body.reason());

        JsonNode result = tusoClient.createCitizenAppointment(actorId, appointmentData);

        // jdbcTemplate.update("""
        //     INSERT INTO appointments (...) VALUES (...)
        //     """, ...);

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

        tusoClient.cancelAppointment(id.toString(), reason);

        // jdbcTemplate.update("""
        //     UPDATE appointments SET status = 'CANCELLED', updated_at = ?
        //     WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'CONFIRMED')
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
