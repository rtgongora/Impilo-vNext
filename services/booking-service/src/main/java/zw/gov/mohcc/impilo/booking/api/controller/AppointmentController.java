package zw.gov.mohcc.impilo.booking.api.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.core.AppointmentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/appointments")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    public record ProviderAppointmentBody(
            @JsonProperty("patient_id") String patientId,
            @JsonProperty("patient_cpid") String patientCpid,
            @JsonProperty("facility_id") @NotBlank String facilityId,
            @JsonProperty("provider_id") String providerId,
            @JsonProperty("provider_name") String providerName,
            @JsonProperty("appointment_type") @NotBlank String appointmentType,
            @JsonProperty("scheduled_at") @NotBlank String scheduledAt,
            @JsonProperty("end_at") String endAt,
            String reason,
            String notes,
            @JsonProperty("resource_id") String resourceId
    ) {}

    public record CitizenAppointmentBody(
            @JsonProperty("facilityId") @NotBlank String facilityId,
            @JsonProperty("appointmentType") @NotBlank String appointmentType,
            @JsonProperty("preferredDate") @NotBlank String preferredDate,
            String reason
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> list(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Long facility = facilityId != null && !facilityId.isBlank() ? Long.parseLong(facilityId.trim()) : null;
        List<AppointmentResponse> items = appointmentService.list(patientId, facility, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(items, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> get(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.get(id), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(@Valid @RequestBody ProviderAppointmentBody body) {
        TrustContext ctx = TrustContextHolder.require();
        String cpid = firstNonBlank(body.patientCpid(), body.patientId(), ctx.actorId());
        AppointmentResponse created = appointmentService.createProvider(new AppointmentService.CreateProviderAppointmentRequest(
                body.patientId(),
                cpid,
                body.facilityId(),
                body.providerId(),
                body.providerName(),
                body.appointmentType(),
                body.scheduledAt(),
                body.endAt(),
                body.reason(),
                body.notes(),
                body.resourceId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirm(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.confirm(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        String reason = body != null && body.get("reason") != null ? body.get("reason").toString() : "Cancelled";
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.cancel(id, reason), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<AppointmentResponse>> checkIn(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID queueId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.checkIn(id, queueId), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<AppointmentResponse>> complete(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.complete(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/no-show")
    public ResponseEntity<ApiResponse<AppointmentResponse>> noShow(@PathVariable UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.noShow(id), ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                appointmentService.reschedule(id, body.get("scheduled_at"), body.get("end_at")),
                ctx.correlationId().toString()));
    }

    @GetMapping("/citizen/{cpid}")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> listCitizen(
            @PathVariable String cpid,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        List<AppointmentResponse> items = appointmentService.listForCitizen(cpid, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(items, ctx.correlationId().toString()));
    }

    @PostMapping("/citizen/{cpid}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createCitizen(
            @PathVariable String cpid,
            @Valid @RequestBody CitizenAppointmentBody body) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Citizen appointment request cpid={} facilityId={}", cpid, body.facilityId());
        AppointmentResponse created = appointmentService.createCitizen(cpid, new AppointmentService.CreateCitizenAppointmentRequest(
                body.facilityId(),
                body.appointmentType(),
                body.preferredDate(),
                body.reason()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, ctx.correlationId().toString()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("patient_cpid or patient_id is required");
    }
}
