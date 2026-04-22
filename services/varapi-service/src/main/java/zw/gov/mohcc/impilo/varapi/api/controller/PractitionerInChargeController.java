package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.PractitionerInChargeService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PractitionerInChargeAssignmentEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Practitioner-In-Charge (PIC) assignments.
 */
@RestController
@RequestMapping("/v1/internal/providers/pic-assignments")
public class PractitionerInChargeController {

    private final PractitionerInChargeService picService;

    public PractitionerInChargeController(PractitionerInChargeService picService) {
        this.picService = picService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createAssignment(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        Long facilityId = Long.valueOf(request.get("facilityId").toString());
        String designationType = (String) request.get("designationType");
        LocalDate startDate = request.get("startDate") != null ?
                LocalDate.parse((String) request.get("startDate")) : LocalDate.now();
        LocalDate endDate = request.get("endDate") != null ?
                LocalDate.parse((String) request.get("endDate")) : null;
        String sourceCouncilReference = (String) request.get("sourceCouncilReference");
        String notes = (String) request.get("notes");

        PractitionerInChargeAssignmentEntity assignment = picService.createAssignment(
                providerId, facilityId, designationType, startDate, endDate, sourceCouncilReference, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "PIC assignment created",
                Map.of("assignmentId", assignment.getId(), "status", assignment.getStatus())
        ));
    }

    @PostMapping("/{assignmentId}/approve")
    public ResponseEntity<GenericResponse> approveAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, String> request) {
        String authorityDecision = request.get("authorityDecision");
        String notes = request.get("notes");
        PractitionerInChargeAssignmentEntity assignment = picService.approveAssignment(assignmentId, authorityDecision, notes);
        return ResponseEntity.ok(GenericResponse.success(
                "PIC assignment approved",
                Map.of("assignmentId", assignment.getId(), "status", assignment.getStatus())
        ));
    }

    @PostMapping("/{assignmentId}/reject")
    public ResponseEntity<GenericResponse> rejectAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        PractitionerInChargeAssignmentEntity assignment = picService.rejectAssignment(assignmentId, reason);
        return ResponseEntity.ok(GenericResponse.success(
                "PIC assignment rejected",
                Map.of("assignmentId", assignment.getId(), "status", assignment.getStatus())
        ));
    }

    @PostMapping("/{assignmentId}/revoke")
    public ResponseEntity<GenericResponse> revokeAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> request) {
        LocalDate revocationDate = request.get("revocationDate") != null ?
                LocalDate.parse((String) request.get("revocationDate")) : LocalDate.now();
        String reason = (String) request.get("reason");
        PractitionerInChargeAssignmentEntity assignment = picService.revokeAssignment(assignmentId, revocationDate, reason);
        return ResponseEntity.ok(GenericResponse.success(
                "PIC assignment revoked",
                Map.of("assignmentId", assignment.getId(), "status", assignment.getStatus())
        ));
    }

    @PostMapping("/{assignmentId}/transfer")
    public ResponseEntity<GenericResponse> transferAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> request) {
        Long newFacilityId = Long.valueOf(request.get("newFacilityId").toString());
        LocalDate transferDate = request.get("transferDate") != null ?
                LocalDate.parse((String) request.get("transferDate")) : LocalDate.now();
        String reason = (String) request.get("reason");

        PractitionerInChargeAssignmentEntity newAssignment = picService.transferAssignment(
                assignmentId, newFacilityId, transferDate, reason);

        return ResponseEntity.ok(GenericResponse.success(
                "PIC assignment transferred",
                Map.of("newAssignmentId", newAssignment.getId(), "status", newAssignment.getStatus())
        ));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<PractitionerInChargeAssignmentEntity> getAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(picService.getAssignment(assignmentId));
    }

    @GetMapping("/provider/{providerId}/active")
    public ResponseEntity<PractitionerInChargeAssignmentEntity> getActiveAssignmentByProvider(@PathVariable Long providerId) {
        PractitionerInChargeAssignmentEntity assignment = picService.getActiveAssignmentByProvider(providerId);
        return assignment != null ? ResponseEntity.ok(assignment) : ResponseEntity.notFound().build();
    }

    @GetMapping("/facility/{facilityId}/active")
    public ResponseEntity<PractitionerInChargeAssignmentEntity> getActiveAssignmentByFacility(@PathVariable Long facilityId) {
        PractitionerInChargeAssignmentEntity assignment = picService.getActiveAssignmentByFacility(facilityId);
        return assignment != null ? ResponseEntity.ok(assignment) : ResponseEntity.notFound().build();
    }

    @GetMapping("/facility/{facilityId}/history")
    public ResponseEntity<List<PractitionerInChargeAssignmentEntity>> getAssignmentHistoryByFacility(@PathVariable Long facilityId) {
        return ResponseEntity.ok(picService.getAssignmentHistoryByFacility(facilityId));
    }

    @GetMapping("/provider/{providerId}/history")
    public ResponseEntity<List<PractitionerInChargeAssignmentEntity>> getAssignmentHistoryByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(picService.getAssignmentHistoryByProvider(providerId));
    }

    @GetMapping("/facility/{facilityId}/eligible-providers")
    public ResponseEntity<List<Map<String, Object>>> getEligibleProviders(@PathVariable Long facilityId) {
        return ResponseEntity.ok(picService.getEligibleProvidersForFacility(facilityId));
    }
}
