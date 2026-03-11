package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Prescription;
import zw.gov.mohcc.impilo.experience.repository.PrescriptionRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.util.*;

/**
 * Pharmacy endpoints.
 * GET  /internal/v1/pharmacy/prescriptions — list prescriptions with status filter, pagination.
 * POST /internal/v1/pharmacy/dispense — dispense a prescription.
 */
@RestController
@RequestMapping("/internal/v1/pharmacy")
public class PharmacyController {

    private final PrescriptionRepository prescriptionRepository;
    private final OutboxService outboxService;

    public PharmacyController(PrescriptionRepository prescriptionRepository,
                              OutboxService outboxService) {
        this.prescriptionRepository = prescriptionRepository;
        this.outboxService = outboxService;
    }

    public record DispenseRequest(
            @NotBlank String prescription_id,
            @NotBlank String dispensed_by
    ) {}

    @GetMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> listPrescriptions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<Prescription> result;
        if (status != null) {
            result = prescriptionRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            result = prescriptionRepository.findByTenantIdAndStatus(tenantId, null, pageable);
        }

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispense")
    @Transactional
    public ResponseEntity<Map<String, Object>> dispense(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DispenseRequest request) {

        UUID prescriptionId = UUID.fromString(request.prescription_id());

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prescription not found: " + request.prescription_id()));

        prescription.dispense(request.dispensed_by());
        prescriptionRepository.save(prescription);

        outboxService.writeOutboxEvent(
                "impilo.experience.pharmacy.dispensed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Prescription",
                prescriptionId.toString(),
                Map.of(
                        "prescription_id", prescriptionId.toString(),
                        "dispensed_by", request.dispensed_by(),
                        "status", "DISPENSED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(prescription));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Prescription p) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", p.getFacilityId());
        attributes.put("patient_id", p.getPatientId());
        attributes.put("encounter_id", p.getEncounterId());
        attributes.put("medication_name", p.getMedicationName());
        attributes.put("dosage", p.getDosage());
        attributes.put("frequency", p.getFrequency());
        attributes.put("duration", p.getDuration());
        attributes.put("quantity", p.getQuantity());
        attributes.put("status", p.getStatus());
        attributes.put("prescribed_by", p.getPrescribedBy());
        attributes.put("dispensed_by", p.getDispensedBy());
        attributes.put("dispensed_at", p.getDispensedAt());
        attributes.put("created_at", p.getCreatedAt());
        attributes.put("updated_at", p.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", p.getId().toString());
        resource.put("type", "Prescription");
        resource.put("attributes", attributes);
        return resource;
    }
}
