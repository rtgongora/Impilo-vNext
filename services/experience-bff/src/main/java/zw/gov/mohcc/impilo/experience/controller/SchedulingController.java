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
 *
 * <p>Manages appointments in the BFF local table and delegates to TUSO's
 * BookingService for resource-level conflict detection and calendar management.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /internal/v1/appointments?patient_id= or facility_id= — list</li>
 *   <li>POST /internal/v1/appointments — create appointment + TUSO booking</li>
 *   <li>POST /internal/v1/appointments/{id}/confirm — confirm appointment</li>
 *   <li>POST /internal/v1/appointments/{id}/cancel — cancel + TUSO cancel</li>
 * </ul>
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
        int offset = page * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT id, patient_id, facility_id, provider_id, provider_name,
                   appointment_type, status, scheduled_at, end_at, reason, notes,
                   tuso_booking_id, created_at, updated_at
            FROM appointments WHERE tenant_id = ?
            """);
        StringBuilder countSql = new StringBuilder(
                "SELECT count(*) FROM appointments WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        List<Object> cParams = new ArrayList<>(List.of(tenantId));

        if (patientId != null) {
            sql.append(" AND patient_id = ?::uuid");
            countSql.append(" AND patient_id = ?::uuid");
            params.add(patientId);
            cParams.add(patientId);
        }
        if (facilityId != null) {
            sql.append(" AND facility_id = ?::uuid");
            countSql.append(" AND facility_id = ?::uuid");
            params.add(facilityId);
            cParams.add(facilityId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
            cParams.add(status);
        }

        sql.append(" ORDER BY scheduled_at ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, cParams.toArray());

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0)));
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

        UUID appointmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime scheduledAt = OffsetDateTime.parse(request.scheduled_at());
        OffsetDateTime endAt = request.end_at() != null
                ? OffsetDateTime.parse(request.end_at())
                : scheduledAt.plusMinutes(30);

        // Create local appointment
        jdbcTemplate.update("""
            INSERT INTO appointments
                (id, tenant_id, patient_id, facility_id, provider_id, provider_name,
                 appointment_type, status, scheduled_at, end_at, reason, notes,
                 created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?, ?, 'SCHEDULED', ?, ?, ?, ?, ?, ?)
            """,
                appointmentId, tenantId, request.patient_id(), request.facility_id(),
                request.provider_id(), request.provider_name(),
                request.appointment_type(), scheduledAt, endAt,
                request.reason(), request.notes(), now, now);

        // Delegate to TUSO for resource booking if resource_id provided
        String tusoBookingId = null;
        if (request.resource_id() != null && !request.resource_id().isBlank()) {
            try {
                JsonNode bookingData = tusoClient.createBooking(
                        UUID.fromString(request.resource_id()),
                        request.patient_id(),
                        request.appointment_type(),
                        scheduledAt, endAt,
                        request.notes());
                if (bookingData != null && bookingData.has("id")) {
                    tusoBookingId = bookingData.get("id").asText();
                    jdbcTemplate.update(
                            "UPDATE appointments SET tuso_booking_id = ?::uuid WHERE id = ?",
                            tusoBookingId, appointmentId);
                }
                log.info("TUSO booking created: {} for appointment {}", tusoBookingId, appointmentId);
            } catch (Exception e) {
                log.warn("TUSO booking failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.appointment.created.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Appointment", appointmentId.toString(),
                Map.of(
                        "appointment_id", appointmentId.toString(),
                        "patient_id", request.patient_id(),
                        "appointment_type", request.appointment_type(),
                        "scheduled_at", scheduledAt.toString(),
                        "status", "SCHEDULED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("provider_name", request.provider_name());
        attributes.put("appointment_type", request.appointment_type());
        attributes.put("status", "SCHEDULED");
        attributes.put("scheduled_at", scheduledAt);
        attributes.put("end_at", endAt);
        attributes.put("reason", request.reason());
        if (tusoBookingId != null) attributes.put("tuso_booking_id", tusoBookingId);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", appointmentId.toString(), "type", "Appointment", "attributes", attributes));
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

        int updated = jdbcTemplate.update("""
            UPDATE appointments SET status = 'CONFIRMED', updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'SCHEDULED'
            """, OffsetDateTime.now(), id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Scheduled appointment not found: " + id);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CONFIRMED"));
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

        int updated = jdbcTemplate.update("""
            UPDATE appointments SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('SCHEDULED', 'CONFIRMED')
            """, OffsetDateTime.now(), id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Active appointment not found: " + id);
        }

        // Cancel in TUSO if booking was bridged
        List<Map<String, Object>> bookingRows = jdbcTemplate.queryForList(
                "SELECT tuso_booking_id FROM appointments WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (!bookingRows.isEmpty() && bookingRows.get(0).get("tuso_booking_id") != null) {
            try {
                tusoClient.cancelBooking(
                        (UUID) bookingRows.get(0).get("tuso_booking_id"), reason);
                log.info("TUSO booking cancelled for appointment {}", id);
            } catch (Exception e) {
                log.warn("TUSO booking cancel failed (non-blocking): {}", e.getMessage());
            }
        }

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

    /**
     * List facility resources available for booking.
     * GET /internal/v1/appointments/resources?facility_id=
     *
     * Proxies TusoServiceClient.listFacilityResources().
     */
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

    /**
     * Check availability for a resource on a specific date.
     * GET /internal/v1/appointments/availability?resource_id=&date=
     *
     * Returns existing bookings for the resource on that date and
     * computes available 30-minute time slots from 08:00 to 17:00.
     */
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

            // Compute occupied slots
            java.util.Set<String> occupiedSlots = new java.util.HashSet<>();
            if (bookings != null && bookings.isArray()) {
                for (JsonNode booking : bookings) {
                    String startStr = booking.has("startTime") ? booking.get("startTime").asText() : null;
                    String endStr = booking.has("endTime") ? booking.get("endTime").asText() : null;
                    if (startStr != null && endStr != null) {
                        try {
                            java.time.OffsetDateTime bStart = java.time.OffsetDateTime.parse(startStr);
                            java.time.OffsetDateTime bEnd = java.time.OffsetDateTime.parse(endStr);
                            // Mark all 30-min slots in this booking as occupied
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

            // Generate all 30-min slots from 08:00 to 16:30
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

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", row.get("patient_id"));
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("provider_id", row.get("provider_id"));
        attributes.put("provider_name", row.get("provider_name"));
        attributes.put("appointment_type", row.get("appointment_type"));
        attributes.put("status", row.get("status"));
        attributes.put("scheduled_at", row.get("scheduled_at"));
        attributes.put("end_at", row.get("end_at"));
        attributes.put("reason", row.get("reason"));
        attributes.put("notes", row.get("notes"));
        attributes.put("tuso_booking_id", row.get("tuso_booking_id"));
        attributes.put("created_at", row.get("created_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "Appointment");
        resource.put("attributes", attributes);
        return resource;
    }
}
