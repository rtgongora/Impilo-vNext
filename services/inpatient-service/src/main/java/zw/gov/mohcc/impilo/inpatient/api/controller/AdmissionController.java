package zw.gov.mohcc.impilo.inpatient.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inpatient.api.dto.CreateAdmissionRequest;
import zw.gov.mohcc.impilo.inpatient.api.dto.PatientLocationView;
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "subjectCpid") String subjectCpid,
            @RequestParam(required = false, name = "patientId") String patientId) {
        String cpid = subjectCpid != null ? subjectCpid : patientId;
        List<AdmissionEntity> admissions = admissionService.listAdmissions(facilityId, status, cpid);
        return ResponseEntity.ok(admissions);
    }

    /**
     * Active admissions for a patient at a facility (ward transfer / discharge correlation).
     */
    @GetMapping("/active")
    public ResponseEntity<List<AdmissionEntity>> findActiveAdmissions(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "facility_id") UUID facilityId) {
        List<AdmissionEntity> admissions =
                admissionService.findActiveAdmissionsForPatientAtFacility(subjectCpid, facilityId);
        return ResponseEntity.ok(admissions);
    }

    /**
     * Resolved current inpatient location for a patient — the active admission's ward + bed
     * with human-readable labels. Returns 204 when the patient is not currently admitted.
     * Backs the experience-shell patient-location badge (G053).
     */
    @GetMapping("/current-location")
    public ResponseEntity<PatientLocationView> getCurrentLocation(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "facility_id", required = false) UUID facilityId) {
        return admissionService.getCurrentLocation(subjectCpid, facilityId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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
