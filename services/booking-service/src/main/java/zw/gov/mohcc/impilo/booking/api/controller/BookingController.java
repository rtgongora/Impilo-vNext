package zw.gov.mohcc.impilo.booking.api.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.api.dto.BookingResponse;
import zw.gov.mohcc.impilo.booking.core.BookingService;
import zw.gov.mohcc.impilo.booking.domain.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public record CreateBookingBody(
            @JsonProperty("client_id") @NotBlank String clientId,
            @JsonProperty("booking_type") @NotBlank String bookingType,
            @JsonProperty("service_id") String serviceId,
            @JsonProperty("service_name") String serviceName,
            @JsonProperty("facility_id") Long facilityId,
            @JsonProperty("provider_id") String providerId,
            @JsonProperty("target_type") String targetType,
            @JsonProperty("target_ref") String targetRef,
            @JsonProperty("preferred_start_at") String preferredStartAt,
            @JsonProperty("preferred_end_at") String preferredEndAt,
            @JsonProperty("preferred_channel") String preferredChannel,
            String location,
            String reason,
            @JsonProperty("clinical_priority") String clinicalPriority,
            @JsonProperty("resource_id") String resourceId,
            String notes,
            @JsonProperty("requires_triage") Boolean requiresTriage,
            @JsonProperty("requires_consent") Boolean requiresConsent,
            @JsonProperty("requires_approval") Boolean requiresApproval
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> list(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        BookingStatus parsed = status != null && !status.isBlank() ? BookingStatus.valueOf(status.toUpperCase()) : null;
        List<BookingResponse> items = bookingService.list(clientId, providerId, facilityId, parsed, page, size);
        return ResponseEntity.ok(ApiResponse.ok(items, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> get(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(bookingService.get(id), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingBody body) {
        TrustContext ctx = TrustContextHolder.require();
        BookingResponse created = bookingService.create(new BookingService.CreateBookingRequest(
                body.clientId(),
                ctx.actorType(),
                BookingType.valueOf(body.bookingType().toUpperCase()),
                body.serviceId(),
                body.serviceName(),
                body.facilityId(),
                body.providerId(),
                body.targetType() != null ? TargetType.valueOf(body.targetType().toUpperCase()) : null,
                body.targetRef(),
                parseInstant(body.preferredStartAt()),
                parseInstant(body.preferredEndAt()),
                body.preferredChannel() != null ? PreferredChannel.valueOf(body.preferredChannel().toUpperCase()) : null,
                body.location(),
                body.reason(),
                body.clinicalPriority() != null ? ClinicalPriority.valueOf(body.clinicalPriority().toUpperCase()) : null,
                parseUuid(body.resourceId()),
                body.notes(),
                Boolean.TRUE.equals(body.requiresTriage()),
                Boolean.TRUE.equals(body.requiresConsent()),
                Boolean.TRUE.equals(body.requiresApproval())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, ctx.correlationId().toString()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        BookingResponse updated = bookingService.update(id, new BookingService.UpdateBookingRequest(
                parseInstant(stringVal(body.get("preferred_start_at"), body.get("preferredStartAt"))),
                parseInstant(stringVal(body.get("preferred_end_at"), body.get("preferredEndAt"))),
                stringVal(body.get("reason")),
                stringVal(body.get("notes"))));
        return ResponseEntity.ok(ApiResponse.ok(updated, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : "Cancelled";
        return ResponseEntity.ok(ApiResponse.ok(bookingService.cancel(id, reason), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/triage")
    public ResponseEntity<ApiResponse<BookingResponse>> triage(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        TriageStatus status = TriageStatus.valueOf(body.getOrDefault("status", "COMPLETED").toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(bookingService.triage(id, status), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<BookingResponse>> approve(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(bookingService.approve(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BookingResponse>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : "Rejected";
        return ResponseEntity.ok(ApiResponse.ok(bookingService.reject(id, reason), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<BookingResponse>> assign(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.assign(id, body.get("provider_id"), body.get("provider_name")),
                ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/redirect")
    public ResponseEntity<ApiResponse<BookingResponse>> redirect(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        Long facilityId = body.get("facility_id") != null
                ? Long.parseLong(body.get("facility_id").toString()) : null;
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        return ResponseEntity.ok(ApiResponse.ok(bookingService.redirect(id, facilityId, reason), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<ApiResponse<BookingResponse>> reserve(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(bookingService.reserve(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<BookingResponse>> confirm(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(bookingService.confirm(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/convert-to-appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> convertToAppointment(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(bookingService.convertToAppointment(id), ctx.correlationId().toString()));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    private static String stringVal(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
