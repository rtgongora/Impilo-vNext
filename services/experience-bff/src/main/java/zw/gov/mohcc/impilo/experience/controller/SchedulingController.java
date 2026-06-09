package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.SchedulingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;
import zw.gov.mohcc.impilo.experience.service.AppointmentCheckInService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Appointment scheduling endpoints with TUSO booking bridge.
 * Delegates appointments to booking-service; TUSO remains for resource calendar.
 */
@RestController
@RequestMapping("/internal/v1/appointments")
public class SchedulingController {

    private static final Logger log = LoggerFactory.getLogger(SchedulingController.class);

    private final BookingServiceClient bookingServiceClient;
    private final TusoServiceClient tusoClient;
    private final SchedulingServiceClient schedulingServiceClient;
    private final AppointmentCheckInService appointmentCheckInService;
    private final AppointmentCommsWorkflowService appointmentComms;

    @Value("${impilo.scheduling.use-remote-slots:true}")
    private boolean useRemoteSlots;

    public SchedulingController(BookingServiceClient bookingServiceClient,
                                TusoServiceClient tusoClient,
                                SchedulingServiceClient schedulingServiceClient,
                                AppointmentCheckInService appointmentCheckInService,
                                AppointmentCommsWorkflowService appointmentComms) {
        this.bookingServiceClient = bookingServiceClient;
        this.tusoClient = tusoClient;
        this.schedulingServiceClient = schedulingServiceClient;
        this.appointmentCheckInService = appointmentCheckInService;
        this.appointmentComms = appointmentComms;
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

        JsonNode appointments = bookingServiceClient.listAppointments(patientId, facilityId, status, page, limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", enrichAppointmentsWithBookingId(appointments));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAppointment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateAppointmentRequest request) {

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

        JsonNode result = bookingServiceClient.createAppointment(appointmentData);
        if (result != null) {
            appointmentComms.onAppointmentCreated(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", enrichAppointmentWithBookingId(result));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirmAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode result = bookingServiceClient.confirmAppointment(id.toString());
        if (result != null) {
            appointmentComms.onAppointmentConfirmed(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        Object data = result != null ? enrichAppointmentWithBookingId(result)
                : Map.of("id", id.toString(), "status", "CONFIRMED");
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Check in a scheduled appointment — bridges scheduling into the outpatient queue spine
     * by starting a PCT journey and enqueueing a walk-in queue item.
     */
    @PostMapping("/{id}/check-in")
    public ResponseEntity<Map<String, Object>> checkInAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return appointmentCheckInService.checkIn(id, requestId, correlationId);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? (String) body.get("reason") : "Cancelled";

        JsonNode cancelled = bookingServiceClient.cancelAppointment(id.toString(), reason);
        if (cancelled != null) {
            appointmentComms.onAppointmentCancelled(cancelled, reason, AppointmentCommsWorkflowService.INITIATOR_STAFF);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        Object data = cancelled != null ? enrichAppointmentWithBookingId(cancelled)
                : Map.of("id", id.toString(), "status", "CANCELLED");
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<Map<String, Object>> rescheduleAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        JsonNode rescheduled = bookingServiceClient.rescheduleAppointment(id.toString(), body);
        if (rescheduled != null) {
            appointmentComms.onAppointmentRescheduled(rescheduled);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rescheduled != null ? enrichAppointmentWithBookingId(rescheduled)
                : Map.of("id", id.toString(), "status", "RESCHEDULED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    public record AppointmentMessageBody(String message, String direction) {}

    /**
     * Two-way appointment messaging — direction {@code citizen_to_provider} or {@code provider_to_citizen}.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> listAppointmentMessages(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", appointmentComms.listMessages(id.toString()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendAppointmentMessage(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestBody AppointmentMessageBody body) {

        if (body.message() == null || body.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "message is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        JsonNode appointment = bookingServiceClient.getAppointment(id.toString());
        if (appointment == null || appointment.isNull()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "APPOINTMENT_NOT_FOUND", "message", "Appointment not found"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        String direction = body.direction() != null ? body.direction().trim().toLowerCase(Locale.ROOT) : "provider_to_citizen";
        if ("provider_to_citizen".equals(direction)) {
            appointmentComms.sendProviderToCitizenMessage(appointment, body.message(), actorId);
        } else {
            appointmentComms.sendCitizenToProviderMessage(appointment, body.message(), actorId);
        }
        return ResponseEntity.ok(Map.of(
                "data", Map.of("appointment_id", id.toString(), "direction", direction, "sent", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
            OffsetDateTime dayStart = localDate.atTime(8, 0).atOffset(ZoneOffset.UTC);
            OffsetDateTime dayEnd = localDate.atTime(17, 0).atOffset(ZoneOffset.UTC);

            JsonNode bookings = tusoClient.listBookings(
                    UUID.fromString(resourceId), dayStart, dayEnd);

            List<OffsetDateTime[]> bookingIntervals = new ArrayList<>();
            if (bookings != null && bookings.isArray()) {
                for (JsonNode booking : bookings) {
                    String startStr = booking.has("startTime") ? booking.get("startTime").asText() : null;
                    String endStr = booking.has("endTime") ? booking.get("endTime").asText() : null;
                    if (startStr != null && endStr != null) {
                        try {
                            OffsetDateTime bStart = OffsetDateTime.parse(startStr);
                            OffsetDateTime bEnd = OffsetDateTime.parse(endStr);
                            bookingIntervals.add(new OffsetDateTime[]{bStart, bEnd});
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            List<Map<String, Object>> slots = new ArrayList<>();
            String slotSource = "BFF_GRID";

            if (useRemoteSlots) {
                try {
                    JsonNode remote = schedulingServiceClient.getSlots(resourceId, date);
                    JsonNode remoteSlots = remote != null ? remote.path("slots") : null;
                    if (remoteSlots != null && remoteSlots.isArray() && !remoteSlots.isEmpty()) {
                        slotSource = "SCHEDULING_SERVICE";
                        for (JsonNode s : remoteSlots) {
                            String startRaw = s.path("start").asText(null);
                            int durationMin = s.path("duration_minutes").asInt(20);
                            boolean engineFree = s.path("available").asBoolean(true);
                            if (startRaw == null || startRaw.isBlank()) {
                                continue;
                            }
                            OffsetDateTime slotStart = parseSchedulingSlotStart(startRaw);
                            OffsetDateTime slotEnd = slotStart.plusMinutes(durationMin);
                            boolean tusoBlocked = overlapsBookings(bookingIntervals, slotStart, slotEnd);
                            String slotLabel = String.format("%02d:%02d", slotStart.getHour(), slotStart.getMinute());
                            slots.add(Map.of(
                                    "time", slotLabel,
                                    "start", slotStart.toString(),
                                    "duration_minutes", durationMin,
                                    "available", engineFree && !tusoBlocked));
                        }
                    }
                } catch (Exception e) {
                    log.debug("scheduling-service slots unavailable, using Tuso-only grid: {}", e.getMessage());
                }
            }

            if (slots.isEmpty()) {
                slotSource = "BFF_GRID";
                java.util.Set<String> occupiedLabels = occupiedHalfHourLabels(bookingIntervals, dayStart, dayEnd);
                OffsetDateTime slotTime = dayStart;
                while (slotTime.isBefore(dayEnd)) {
                    String slotLabel = String.format("%02d:%02d",
                            slotTime.getHour(), slotTime.getMinute());
                    boolean available = !occupiedLabels.contains(slotLabel);
                    slots.add(Map.of("time", slotLabel, "available", available));
                    slotTime = slotTime.plusMinutes(30);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "date", date,
                    "resource_id", resourceId,
                    "slots", slots,
                    "existing_bookings", bookings != null ? bookings : List.of()
            ));
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "slot_source", slotSource));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to check availability: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("date", date, "resource_id", resourceId, "slots", List.of()));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }
    }

    private static OffsetDateTime parseSchedulingSlotStart(String startRaw) {
        try {
            return OffsetDateTime.parse(startRaw);
        } catch (Exception ignored) {
        }
        LocalDateTime ldt = LocalDateTime.parse(startRaw);
        return ldt.atOffset(ZoneOffset.UTC);
    }

    private static boolean overlapsBookings(List<OffsetDateTime[]> intervals,
                                            OffsetDateTime slotStart, OffsetDateTime slotEnd) {
        for (OffsetDateTime[] iv : intervals) {
            if (iv.length == 2 && slotStart.isBefore(iv[1]) && slotEnd.isAfter(iv[0])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Half-hour labels (HH:mm UTC) touched by each booking — matches legacy BFF slot stepping.
     */
    private static String textOr(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value != null && !value.isBlank() ? value : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Additively surfaces {@code bookingId} on appointment payloads when booking-service provides it.
     */
    private static Object enrichAppointmentWithBookingId(JsonNode appointment) {
        if (appointment == null || appointment.isNull()) {
            return appointment;
        }
        if (appointment.isObject()) {
            Map<String, Object> copy = new LinkedHashMap<>();
            appointment.fields().forEachRemaining(entry -> copy.put(entry.getKey(), entry.getValue()));
            String bookingId = firstNonBlank(
                    textOr(appointment, "bookingId"),
                    textOr(appointment, "booking_id"));
            if (bookingId != null) {
                copy.putIfAbsent("booking_id", bookingId);
                copy.putIfAbsent("bookingId", bookingId);
            }
            return copy;
        }
        return appointment;
    }

    private static Object enrichAppointmentsWithBookingId(JsonNode appointments) {
        if (appointments == null || appointments.isNull()) {
            return appointments;
        }
        if (appointments.isArray()) {
            List<Object> enriched = new ArrayList<>();
            appointments.forEach(node -> enriched.add(enrichAppointmentWithBookingId(node)));
            return enriched;
        }
        return enrichAppointmentWithBookingId(appointments);
    }

    private static Set<String> occupiedHalfHourLabels(List<OffsetDateTime[]> intervals,
                                                     OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        Set<String> labels = new HashSet<>();
        for (OffsetDateTime[] iv : intervals) {
            if (iv.length != 2) {
                continue;
            }
            OffsetDateTime bStart = iv[0];
            OffsetDateTime bEnd = iv[1];
            OffsetDateTime slot = bStart;
            while (slot.isBefore(bEnd)) {
                if (!slot.isBefore(dayStart) && slot.isBefore(dayEnd)) {
                    labels.add(String.format("%02d:%02d", slot.getHour(), slot.getMinute()));
                }
                slot = slot.plusMinutes(30);
            }
        }
        return labels;
    }
}
