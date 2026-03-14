package zw.gov.mohcc.impilo.inpatient.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.api.dto.CreateAdmissionRequest;
import zw.gov.mohcc.impilo.inpatient.api.dto.TransferRequest;
import zw.gov.mohcc.impilo.inpatient.core.AdmissionService;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.TransferEntity;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for inpatient admissions, transfers, and discharges.
 * All endpoints are under {@code /internal/v1/admissions}.
 */
@RestController
@RequestMapping("/internal/v1/admissions")
public class AdmissionController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionController.class);

    private final AdmissionService admissionService;

    public AdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    /**
     * List admissions, optionally filtered by facilityId and/or status.
     */
    @GetMapping
    public ResponseEntity<List<AdmissionEntity>> listAdmissions(
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) String status) {
        List<AdmissionEntity> admissions = admissionService.listAdmissions(facilityId, status);
        return ResponseEntity.ok(admissions);
    }

    /**
     * Get a single admission by its unique reference.
     */
    @GetMapping("/{admissionRef}")
    public ResponseEntity<AdmissionEntity> getAdmission(@PathVariable UUID admissionRef) {
        AdmissionEntity admission = admissionService.getAdmission(admissionRef);
        return ResponseEntity.ok(admission);
    }

    /**
     * Create a new admission.
     */
    @PostMapping
    public ResponseEntity<AdmissionEntity> createAdmission(
            @Valid @RequestBody CreateAdmissionRequest request) {
        AdmissionEntity admission = admissionService.createAdmission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(admission);
    }

    /**
     * Transfer an admitted patient to a new ward/bed.
     */
    @PostMapping("/{admissionRef}/transfer")
    public ResponseEntity<TransferEntity> transferPatient(
            @PathVariable UUID admissionRef,
            @Valid @RequestBody TransferRequest request) {
        TransferEntity transfer = admissionService.transferPatient(admissionRef, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
    }

    /**
     * Discharge an admitted patient.
     */
    @PostMapping("/{admissionRef}/discharge")
    public ResponseEntity<AdmissionEntity> dischargePatient(@PathVariable UUID admissionRef) {
        AdmissionEntity admission = admissionService.dischargePatient(admissionRef);
        return ResponseEntity.ok(admission);
    }
}
