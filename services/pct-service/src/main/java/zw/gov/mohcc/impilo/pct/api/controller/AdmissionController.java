package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.AdmitRequest;
import zw.gov.mohcc.impilo.pct.api.dto.AssignBedRequest;
import zw.gov.mohcc.impilo.pct.core.AdmissionWorkflow;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

/**
 * REST controller for inpatient admission workflow operations.
 *
 * <p>Provides endpoints for requesting, approving, and finalizing
 * patient admissions, as well as assigning or reassigning beds.</p>
 */
@RestController
@RequestMapping("/v1")
public class AdmissionController {

    private static final Logger log = LoggerFactory.getLogger(AdmissionController.class);

    private final AdmissionWorkflow admissionWorkflow;

    public AdmissionController(AdmissionWorkflow admissionWorkflow) {
        this.admissionWorkflow = admissionWorkflow;
    }

    /**
     * Request admission for a patient journey.
     *
     * @param id      the journey identifier
     * @param request the admission request containing ward and bed details
     * @return the created admission entity
     */
    @PostMapping("/journeys/{id}/admit")
    public ResponseEntity<ApiResponse<AdmissionEntity>> requestAdmission(
            @PathVariable String id,
            @Valid @RequestBody AdmitRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        AdmissionEntity admission = admissionWorkflow.requestAdmission(
                id, request.wardId(), request.bedId());

        return ResponseEntity.ok(ApiResponse.ok(admission, correlationId));
    }

    /**
     * Approve a pending admission request.
     *
     * @param id the admission identifier
     * @return the updated admission entity
     */
    @PostMapping("/admissions/{id}/approve")
    public ResponseEntity<ApiResponse<AdmissionEntity>> approveAdmission(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        AdmissionEntity admission = admissionWorkflow.approveAdmission(id);

        return ResponseEntity.ok(ApiResponse.ok(admission, correlationId));
    }

    /**
     * Admit the patient (physically place in ward/bed).
     *
     * @param id the admission identifier
     * @return the updated admission entity
     */
    @PostMapping("/admissions/{id}/admit")
    public ResponseEntity<ApiResponse<AdmissionEntity>> admitPatient(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        AdmissionEntity admission = admissionWorkflow.admitPatient(id);

        return ResponseEntity.ok(ApiResponse.ok(admission, correlationId));
    }

    /**
     * Assign or reassign a bed for an admitted patient.
     *
     * @param id      the admission identifier
     * @param request the bed assignment request containing ward and bed
     * @return the updated admission entity
     */
    @PostMapping("/admissions/{id}/assign-bed")
    public ResponseEntity<ApiResponse<AdmissionEntity>> assignBed(
            @PathVariable UUID id,
            @Valid @RequestBody AssignBedRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        AdmissionEntity admission = admissionWorkflow.assignBed(
                id, request.wardId(), request.bedId());

        return ResponseEntity.ok(ApiResponse.ok(admission, correlationId));
    }
}
