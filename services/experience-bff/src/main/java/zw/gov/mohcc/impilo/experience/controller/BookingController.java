package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentCommsWorkflowService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Booking transaction lifecycle — delegates to booking-service.
 */
@RestController
@RequestMapping("/internal/v1/bookings")
public class BookingController {

    private final BookingServiceClient bookingServiceClient;
    private final AppointmentCommsWorkflowService appointmentComms;

    public BookingController(BookingServiceClient bookingServiceClient,
                             AppointmentCommsWorkflowService appointmentComms) {
        this.bookingServiceClient = bookingServiceClient;
        this.appointmentComms = appointmentComms;
    }

    public record CreateBookingBody(
            String client_id,
            @NotBlank String booking_type,
            String service_id,
            String service_name,
            @NotBlank String facility_id,
            String provider_id,
            String target_type,
            String target_ref,
            String preferred_start_time,
            String preferred_end_time,
            String preferred_channel,
            String reason_for_booking,
            String clinical_priority
    ) {}

    public record UpdateBookingBody(
            String preferred_start_time,
            String preferred_end_time,
            String reason_for_booking,
            String provider_id
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listBookings(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "client_id") String clientId,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "provider_id") String providerId,
            @RequestParam(required = false, name = "booking_status") String bookingStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        JsonNode bookings = bookingServiceClient.listBookings(
                clientId, facilityId, providerId, bookingStatus, page, limit);
        return ok(bookings, requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode booking = bookingServiceClient.getBooking(id.toString());
        return ok(booking, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody CreateBookingBody body) {

        Map<String, Object> payload = mapCreateBody(body, actorId);
        JsonNode created = bookingServiceClient.createBooking(payload);
        if (created != null) {
            appointmentComms.onBookingCreated(created);
        }
        return ok(created, requestId, correlationId, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody UpdateBookingBody body) {

        Map<String, Object> payload = new LinkedHashMap<>();
        if (body.preferred_start_time() != null) payload.put("preferredStartTime", body.preferred_start_time());
        if (body.preferred_end_time() != null) payload.put("preferredEndTime", body.preferred_end_time());
        if (body.reason_for_booking() != null) payload.put("reasonForBooking", body.reason_for_booking());
        if (body.provider_id() != null) payload.put("providerId", body.provider_id());

        JsonNode updated = bookingServiceClient.updateBooking(id.toString(), payload);
        return ok(updated, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        JsonNode cancelled = bookingServiceClient.cancelBooking(id.toString(), reason);
        if (cancelled != null) {
            appointmentComms.onBookingCancelled(cancelled, reason, AppointmentCommsWorkflowService.INITIATOR_STAFF);
        }
        return ok(cancelled, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/triage")
    public ResponseEntity<Map<String, Object>> triageBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        JsonNode triaged = bookingServiceClient.triageBooking(id.toString(), body);
        return ok(triaged, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode approved = bookingServiceClient.approveBooking(id.toString());
        if (approved != null) {
            appointmentComms.onBookingApproved(approved);
        }
        return ok(approved, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : null;
        JsonNode rejected = bookingServiceClient.rejectBooking(id.toString(), reason);
        if (rejected != null) {
            appointmentComms.onBookingRejected(rejected, reason);
        }
        return ok(rejected, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        JsonNode assigned = bookingServiceClient.assignBooking(id.toString(), body);
        return ok(assigned, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<Map<String, Object>> reserveBooking(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode reserved = bookingServiceClient.reserveBooking(id.toString());
        return ok(reserved, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<Map<String, Object>> convertToAppointment(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode appointment = bookingServiceClient.convertBookingToAppointment(id.toString());
        if (appointment != null) {
            appointmentComms.onAppointmentCreated(appointment);
        }
        return ok(appointment, requestId, correlationId, HttpStatus.CREATED);
    }

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "target_type") String targetType,
            @RequestParam(name = "target_ref") String targetRef,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam String date) {

        JsonNode slots = bookingServiceClient.checkAvailability(targetType, targetRef, facilityId, date);
        return ok(slots, requestId, correlationId, HttpStatus.OK);
    }

    private static Map<String, Object> mapCreateBody(CreateBookingBody body, String actorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String clientId = body.client_id() != null && !body.client_id().isBlank() ? body.client_id() : actorId;
        if (clientId != null) payload.put("client_id", clientId);
        payload.put("booking_type", body.booking_type());
        if (body.service_id() != null) payload.put("service_id", body.service_id());
        if (body.service_name() != null) payload.put("service_name", body.service_name());
        payload.put("facility_id", body.facility_id());
        if (body.provider_id() != null) payload.put("provider_id", body.provider_id());
        if (body.target_type() != null) payload.put("target_type", body.target_type());
        if (body.target_ref() != null) payload.put("target_ref", body.target_ref());
        if (body.preferred_start_time() != null) payload.put("preferred_start_at", body.preferred_start_time());
        if (body.preferred_end_time() != null) payload.put("preferred_end_at", body.preferred_end_time());
        if (body.preferred_channel() != null) payload.put("preferred_channel", body.preferred_channel());
        if (body.reason_for_booking() != null) payload.put("reason", body.reason_for_booking());
        if (body.clinical_priority() != null) payload.put("clinical_priority", body.clinical_priority());
        return payload;
    }

    private static ResponseEntity<Map<String, Object>> ok(
            Object data, String requestId, String correlationId, HttpStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(status).body(response);
    }
}
