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

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Pharmacy endpoints.
 * GET  /internal/v1/pharmacy/prescriptions — list prescriptions with status/patient filter, pagination.
 * POST /internal/v1/pharmacy/prescriptions — create a prescription.
 * POST /internal/v1/pharmacy/dispense — dispense a prescription.
 */
@RestController
@RequestMapping("/internal/v1/pharmacy")
public class PharmacyController {

    private final PrescriptionRepository prescriptionRepository;
    private final OutboxService outboxService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public PharmacyController(PrescriptionRepository prescriptionRepository,
                              OutboxService outboxService,
                              org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.prescriptionRepository = prescriptionRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreatePrescriptionRequest(
            @NotBlank String patient_id,
            String facility_id,
            String encounter_id,
            @NotBlank String medication_name,
            String generic_name,
            String dosage,
            String route,
            String frequency,
            String duration,
            Integer quantity,
            String instructions,
            String indication,
            @NotBlank String prescribed_by
    ) {}

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
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        if (patientId != null) {
            List<Prescription> patientRx = prescriptionRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId));
            List<Map<String, Object>> data = patientRx.stream().map(this::toResource).toList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }

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

    @PostMapping("/prescriptions")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPrescription(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrescriptionRequest request) {

        UUID rxId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO prescriptions
                (id, tenant_id, facility_id, patient_id, encounter_id, medication_name,
                 generic_name, dosage, route, frequency, duration, quantity, instructions,
                 indication, status, prescribed_by, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
            """,
                rxId, tenantId, request.facility_id(), request.patient_id(),
                request.encounter_id(), request.medication_name(),
                request.generic_name(), request.dosage(), request.route(),
                request.frequency(), request.duration(), request.quantity(),
                request.instructions(), request.indication(),
                request.prescribed_by(), now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.pharmacy.prescribed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Prescription",
                rxId.toString(),
                Map.of(
                        "prescription_id", rxId.toString(),
                        "patient_id", request.patient_id(),
                        "medication_name", request.medication_name(),
                        "status", "PENDING"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("medication_name", request.medication_name());
        attributes.put("generic_name", request.generic_name());
        attributes.put("dosage", request.dosage());
        attributes.put("route", request.route());
        attributes.put("frequency", request.frequency());
        attributes.put("duration", request.duration());
        attributes.put("quantity", request.quantity());
        attributes.put("instructions", request.instructions());
        attributes.put("indication", request.indication());
        attributes.put("status", "PENDING");
        attributes.put("prescribed_by", request.prescribed_by());
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", rxId.toString(), "type", "Prescription", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
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

        // Enrich outbox event with full medication data for pharmacy-service consumption
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("prescription_id", prescriptionId.toString());
        eventPayload.put("patient_id", prescription.getPatientId().toString());
        eventPayload.put("facility_id", prescription.getFacilityId() != null ? prescription.getFacilityId().toString() : null);
        eventPayload.put("encounter_id", prescription.getEncounterId() != null ? prescription.getEncounterId().toString() : null);
        eventPayload.put("medication_name", prescription.getMedicationName());
        eventPayload.put("dosage", prescription.getDosage());
        eventPayload.put("frequency", prescription.getFrequency());
        eventPayload.put("quantity", prescription.getQuantity());
        eventPayload.put("dispensed_by", request.dispensed_by());
        eventPayload.put("status", "DISPENSED");

        // Include V13 fields from JDBC
        try {
            List<Map<String, Object>> rxRows = jdbcTemplate.queryForList(
                    "SELECT route, instructions, indication, generic_name FROM prescriptions WHERE id = ?",
                    prescriptionId);
            if (!rxRows.isEmpty()) {
                Map<String, Object> rxData = rxRows.get(0);
                eventPayload.put("route", rxData.get("route"));
                eventPayload.put("instructions", rxData.get("instructions"));
                eventPayload.put("indication", rxData.get("indication"));
                eventPayload.put("generic_name", rxData.get("generic_name"));
            }
        } catch (Exception e) {
            // V13 columns may not exist yet
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.pharmacy.dispensed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Prescription",
                prescriptionId.toString(),
                eventPayload,
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

    /**
     * Cancel a prescription.
     * POST /internal/v1/pharmacy/prescriptions/{id}/cancel
     */
    @PostMapping("/prescriptions/{id}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelPrescription(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Prescription prescription = prescriptionRepository.findById(id)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + id));

        prescription.cancel();
        prescriptionRepository.save(prescription);

        outboxService.writeOutboxEvent(
                "impilo.experience.pharmacy.cancelled.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Prescription", id.toString(),
                Map.of("prescription_id", id.toString(), "status", "CANCELLED"),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(prescription));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
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

        // V13 fields — read via JDBC since JPA entity may not have them mapped
        try {
            List<Map<String, Object>> extraRows = jdbcTemplate.queryForList(
                    "SELECT route, instructions, indication, generic_name FROM prescriptions WHERE id = ?",
                    p.getId());
            if (!extraRows.isEmpty()) {
                Map<String, Object> extra = extraRows.get(0);
                attributes.put("route", extra.get("route"));
                attributes.put("instructions", extra.get("instructions"));
                attributes.put("indication", extra.get("indication"));
                attributes.put("generic_name", extra.get("generic_name"));
            }
        } catch (Exception e) {
            // V13 columns may not exist yet during migration
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", p.getId().toString());
        resource.put("type", "Prescription");
        resource.put("attributes", attributes);
        return resource;
    }
}
