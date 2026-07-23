package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.CancelPrescriptionRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.CreatePrescriptionRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.PrescriptionResourceDto;
import zw.gov.mohcc.impilo.pharmacy.core.PrescriptionService;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PrescriptionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>FROZEN LEGACY SILO (OD-13, Vol II §17.1 deprecation note).</b> The flat
 * {@code rx_prescriptions} model is a migration source, not an authoring target:
 * no new writers, no new fields. The prescription aggregate (versions, JWS signing,
 * repeats counter, tokens) lives in <b>oros-service</b> {@code /v1/prescriptions}
 * (OF-B2). This API remains readable for continuity and MUST NOT be presented on
 * any new surface; the create path logs a deprecation warning per call.
 */
@RestController
@RequestMapping("/v1/prescriptions")
public class PrescriptionController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PrescriptionController.class);

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    /** @deprecated frozen legacy path (OD-13) — author prescriptions in oros-service. */
    @Deprecated
    @PostMapping
    public ResponseEntity<ApiResponse<PrescriptionResourceDto>> createPrescription(
            @Valid @RequestBody CreatePrescriptionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        log.warn("DEPRECATED legacy prescription create used (OD-13 frozen silo) — "
                + "author via oros-service /v1/prescriptions instead. correlationId={}", correlationId);
        try {
            PrescriptionEntity created = prescriptionService.createPrescription(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(PrescriptionResourceDto.from(created), correlationId));
        } catch (IllegalArgumentException validation) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "VALIDATION_FAILED", validation.getMessage(), 400, correlationId));
        }
    }

    @GetMapping("/patient/{cpid}")
    public ResponseEntity<ApiResponse<List<PrescriptionResourceDto>>> listPatientPrescriptions(
            @PathVariable String cpid,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            var result = prescriptionService.listPatientPrescriptions(cpid, status, PageRequest.of(page, Math.min(size, 100)));
            List<PrescriptionResourceDto> data = result.getContent().stream()
                    .map(PrescriptionResourceDto::from)
                    .toList();
            return ResponseEntity.ok(ApiResponse.ok(data, correlationId));
        } catch (IllegalArgumentException validation) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "VALIDATION_FAILED", validation.getMessage(), 400, correlationId));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResourceDto>> getPrescription(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            PrescriptionEntity found = prescriptionService.getPrescription(id);
            return ResponseEntity.ok(ApiResponse.ok(PrescriptionResourceDto.from(found), correlationId));
        } catch (IllegalArgumentException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "PRESCRIPTION_NOT_FOUND", notFound.getMessage(), 404, correlationId));
        } catch (SecurityException forbidden) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    "FORBIDDEN", forbidden.getMessage(), 403, correlationId));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PrescriptionResourceDto>> cancelPrescription(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelPrescriptionRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            PrescriptionEntity cancelled = prescriptionService.cancelPrescription(
                    id, request != null ? request.reason() : null);
            return ResponseEntity.ok(ApiResponse.ok(PrescriptionResourceDto.from(cancelled), correlationId));
        } catch (IllegalArgumentException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "PRESCRIPTION_NOT_FOUND", notFound.getMessage(), 404, correlationId));
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "PRESCRIPTION_STATE_CONFLICT", conflict.getMessage(), 409, correlationId));
        }
    }

    @PostMapping("/{id}/refill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestRefill(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> payload) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            PrescriptionEntity refill = prescriptionService.requestRefill(id, payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                    "prescription_id", refill.getPrescriptionId().toString(),
                    "status", "REFILL_REQUESTED",
                    "requested_at", refill.getRefillRequestedAt()
            ), correlationId));
        } catch (IllegalArgumentException notFound) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "PRESCRIPTION_NOT_FOUND", notFound.getMessage(), 404, correlationId));
        }
    }

    @PostMapping("/{id}/dispense")
    public ResponseEntity<ApiResponse<PrescriptionResourceDto>> markDispensed(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            String dispensedBy = body != null && body.get("dispensed_by") != null
                    ? String.valueOf(body.get("dispensed_by"))
                    : TrustContextHolder.require().actorId();
            PrescriptionEntity dispensed = prescriptionService.markDispensed(id, dispensedBy);
            return ResponseEntity.ok(ApiResponse.ok(PrescriptionResourceDto.from(dispensed), correlationId));
        } catch (IllegalArgumentException notFoundOrValidation) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "PRESCRIPTION_NOT_FOUND", notFoundOrValidation.getMessage(), 404, correlationId));
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "PRESCRIPTION_STATE_CONFLICT", conflict.getMessage(), 409, correlationId));
        }
    }
}
