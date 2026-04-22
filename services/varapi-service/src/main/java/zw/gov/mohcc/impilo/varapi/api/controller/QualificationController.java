package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.QualificationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderQualificationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider qualifications.
 */
@RestController
@RequestMapping("/v1/internal/providers/qualifications")
public class QualificationController {

    private final QualificationService qualificationService;

    public QualificationController(QualificationService qualificationService) {
        this.qualificationService = qualificationService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createQualification(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        String qualificationName = (String) request.get("qualificationName");
        String qualificationCode = (String) request.get("qualificationCode");
        String awardingBody = (String) request.get("awardingBody");
        LocalDate dateAwarded = request.get("dateAwarded") != null ?
                LocalDate.parse((String) request.get("dateAwarded")) : null;
        String qualificationLevel = (String) request.get("qualificationLevel");
        String professionalCategory = (String) request.get("professionalCategory");
        String documentReference = (String) request.get("documentReference");
        String notes = (String) request.get("notes");

        ProviderQualificationEntity qualification = qualificationService.createQualification(
                providerId, qualificationName, qualificationCode, awardingBody, dateAwarded,
                qualificationLevel, professionalCategory, documentReference, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Qualification created",
                Map.of("qualificationId", qualification.getId(), "verificationStatus", qualification.getVerificationStatus())
        ));
    }

    @PostMapping("/{qualificationId}/submit")
    public ResponseEntity<GenericResponse> submitForVerification(@PathVariable Long qualificationId) {
        ProviderQualificationEntity qualification = qualificationService.submitForVerification(qualificationId);
        return ResponseEntity.ok(GenericResponse.success(
                "Qualification submitted for verification",
                Map.of("qualificationId", qualification.getId(), "verificationStatus", qualification.getVerificationStatus())
        ));
    }

    @PostMapping("/{qualificationId}/verify")
    public ResponseEntity<GenericResponse> completeVerification(
            @PathVariable Long qualificationId,
            @RequestBody Map<String, Object> request) {
        String verificationStatus = (String) request.get("verificationStatus");
        LocalDate verificationDate = request.get("verificationDate") != null ?
                LocalDate.parse((String) request.get("verificationDate")) : LocalDate.now();
        String verifiedBy = (String) request.get("verifiedBy");
        String notes = (String) request.get("notes");

        ProviderQualificationEntity qualification = qualificationService.completeVerification(
                qualificationId, verificationStatus, verificationDate, verifiedBy, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Verification completed",
                Map.of("qualificationId", qualification.getId(), "verificationStatus", qualification.getVerificationStatus())
        ));
    }

    @PostMapping("/{qualificationId}/supersede")
    public ResponseEntity<GenericResponse> supersedeQualification(
            @PathVariable Long qualificationId,
            @RequestBody Map<String, Object> request) {
        Long newQualificationId = Long.valueOf(request.get("newQualificationId").toString());
        String reason = (String) request.get("reason");

        ProviderQualificationEntity superseded = qualificationService.supersedeQualification(
                qualificationId, newQualificationId, reason);

        return ResponseEntity.ok(GenericResponse.success(
                "Qualification superseded",
                Map.of("supersededId", qualificationId, "newQualificationId", newQualificationId)
        ));
    }

    @GetMapping("/{qualificationId}")
    public ResponseEntity<ProviderQualificationEntity> getQualification(@PathVariable Long qualificationId) {
        return ResponseEntity.ok(qualificationService.getQualification(qualificationId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderQualificationEntity>> getQualificationsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(qualificationService.getQualificationsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/verified")
    public ResponseEntity<List<ProviderQualificationEntity>> getVerifiedQualifications(@PathVariable Long providerId) {
        return ResponseEntity.ok(qualificationService.getVerifiedQualifications(providerId));
    }

    @GetMapping("/provider/{providerId}/pending-verification")
    public ResponseEntity<List<ProviderQualificationEntity>> getPendingVerificationQualifications(@PathVariable Long providerId) {
        return ResponseEntity.ok(qualificationService.getPendingVerificationQualifications(providerId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProviderQualificationEntity>> searchQualifications(
            @RequestParam(required = false) String awardingBody,
            @RequestParam(required = false) String qualificationLevel,
            @RequestParam(required = false) String professionalCategory) {
        return ResponseEntity.ok(qualificationService.searchQualifications(awardingBody, qualificationLevel, professionalCategory));
    }
}
