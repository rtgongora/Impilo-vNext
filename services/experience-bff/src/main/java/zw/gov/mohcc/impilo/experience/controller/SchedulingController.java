package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Appointment scheduling endpoints with TUSO booking bridge.
 * Delegates to TusoServiceClient for canonical booking lifecycle.
 */
@RestController
@RequestMapping("/internal/v1/appointments")
public class SchedulingController {

    private static final Logger log = LoggerFactory.getLogger(SchedulingController.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final TusoServiceClient tusoClient;

    public SchedulingController(JdbcTemplate jdbcTemplate,
                                OutboxService outboxService,
                                TusoServiceClient tusoClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.tusoClient = tusoClient;
    }

    public record CreateAppointmentRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            String provider_id,
            String provider_name,
            @NotBlank String appointment_type,
            @NotBlank String scheduled_at,
            String end_at,
            String reason,
            String notes,
            String resource_id
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAppointments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);

        // STRANGLER: migrated — delegate to TusoServiceClient
        JsonNode appointments = tusoClient.listAppointments(patientId, facilityId, status, page, limit);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from appointments table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", appointments);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createAppointment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateAppointmentRequest request) {

        // STRANGLER: migrated — delegate to TusoServiceClient
        Map<String, Object> appointmentData = new LinkedHashMap<>();
        appointmentData.put("patient_id", request.patient_id());
        appointmentData.put("facility_id", request.facility_id());
        appointmentData.put("provider_id", request.provider_id());
        appointmentData.put("provider_name", request.provider_name());
        appointmentData.put("appointment_type", request.appointment_type());
        appointmentData.put("scheduled_at", request.scheduled_at());
        appointmentData.put("end_at", request.end_at());
        appointmentData.put("reason", request.reason());
        appointmentData.put("notes", request.notes());
        appointmentData.put("resource_id", request.resource_id());
        appointmentData.put("tenant_id", tenantId);

        JsonNode result = tusoClient.createAppointment(appointmentData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into appointments + TUSO booking bridge
        // jdbcTemplate.update("""
        //     INSERT INTO appointments (...) VALUES (...)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.appointment.created.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Appointment", requestId,
                Map.of(
                        "patient_id", request.patient_id(),
                        "appointment_type", request.appointment_type(),
                        "scheduled_at", request.scheduled_at(),
                        "status", "SCHEDULED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to TusoServiceClient
        JsonNode result = tusoClient.confirmAppointment(id.toString());

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on appointments
        // jdbcTemplate.update("UPDATE appointments SET status = 'CONFIRMED' ...", ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result != null ? result : Map.of("id", id.toString(), "status", "CONFIRMED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? (String) body.get("reason") : "Cancelled";

        // STRANGLER: migrated — delegate to TusoServiceClient (handles both local + TUSO booking cancel)
        tusoClient.cancelAppointment(id.toString(), reason);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on appointments + tusoClient.cancelBooking
        // jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED' ...", ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.appointment.cancelled.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Appointment", id.toString(),
                Map.of("appointment_id", id.toString(), "status", "CANCELLED"),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> listResources(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "resource_type") String resourceType) {

        try {
            JsonNode resources = tusoClient.listFacilityResources(
                    Long.parseLong(facilityId), resourceType);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", resources);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to fetch facility resources: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", List.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "resource_id") String resourceId,
            @RequestParam String date) {

        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            java.time.OffsetDateTime dayStart = localDate.atTime(8, 0)
                    .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
            java.time.OffsetDateTime dayEnd = localDate.atTime(17, 0)
                    .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();

            JsonNode bookings = tusoClient.listBookings(
                    UUID.fromString(resourceId), dayStart, dayEnd);

            java.util.Set<String> occupiedSlots = new java.util.HashSet<>();
            if (bookings != null && bookings.isArray()) {
                for (JsonNode booking : bookings) {
                    String startStr = booking.has("startTime") ? booking.get("startTime").asText() : null;
                    String endStr = booking.has("endTime") ? booking.get("endTime").asText() : null;
                    if (startStr != null && endStr != null) {
                        try {
                            java.time.OffsetDateTime bStart = java.time.OffsetDateTime.parse(startStr);
                            java.time.OffsetDateTime bEnd = java.time.OffsetDateTime.parse(endStr);
                            java.time.OffsetDateTime slot = bStart;
                            while (slot.isBefore(bEnd)) {
                                occupiedSlots.add(String.format("%02d:%02d",
                                        slot.getHour(), slot.getMinute()));
                                slot = slot.plusMinutes(30);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            List<Map<String, Object>> slots = new java.util.ArrayList<>();
            java.time.OffsetDateTime slotTime = dayStart;
            while (slotTime.isBefore(dayEnd)) {
                String slotLabel = String.format("%02d:%02d",
                        slotTime.getHour(), slotTime.getMinute());
                boolean available = !occupiedSlots.contains(slotLabel);
                slots.add(Map.of("time", slotLabel, "available", available));
                slotTime = slotTime.plusMinutes(30);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "date", date,
                    "resource_id", resourceId,
                    "slots", slots,
                    "existing_bookings", bookings != null ? bookings : List.of()
            ));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to check availability: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("date", date, "resource_id", resourceId, "slots", List.of()));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }
    }
}
