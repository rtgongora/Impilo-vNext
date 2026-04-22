package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.AffiliationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider facility affiliations.
 */
@RestController
@RequestMapping("/v1/internal/providers/affiliations")
public class AffiliationController {

    private final AffiliationService affiliationService;

    public AffiliationController(AffiliationService affiliationService) {
        this.affiliationService = affiliationService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createAffiliation(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        Long facilityId = Long.valueOf(request.get("facilityId").toString());
        String affiliationType = (String) request.get("affiliationType");
        String roleTitle = (String) request.get("roleTitle");
        LocalDate startDate = request.get("startDate") != null ?
                LocalDate.parse((String) request.get("startDate")) : null;
        LocalDate endDate = request.get("endDate") != null ?
                LocalDate.parse((String) request.get("endDate")) : null;
        String notes = (String) request.get("notes");
        Boolean primaryFlag = (Boolean) request.get("primaryFlag");

        ProviderAffiliationEntity affiliation = affiliationService.createAffiliation(
                providerId, facilityId, affiliationType, roleTitle, startDate, endDate, notes);

        if (Boolean.TRUE.equals(primaryFlag)) {
            affiliation.setPrimaryFlag(true);
            affiliationService.submitForApproval(affiliation.getId());
            affiliation = affiliationService.approveAffiliation(affiliation.getId(), "AUTO_APPROVED", null);
        }

        return ResponseEntity.ok(GenericResponse.success(
                "Affiliation created",
                Map.of("affiliationId", affiliation.getId(), "status", affiliation.getStatus())
        ));
    }

    @PostMapping("/{affiliationId}/submit")
    public ResponseEntity<GenericResponse> submitForApproval(@PathVariable Long affiliationId) {
        ProviderAffiliationEntity affiliation = affiliationService.submitForApproval(affiliationId);
        return ResponseEntity.ok(GenericResponse.success(
                "Affiliation submitted for approval",
                Map.of("affiliationId", affiliation.getId(), "approvalState", affiliation.getApprovalState())
        ));
    }

    @PostMapping("/{affiliationId}/approve")
    public ResponseEntity<GenericResponse> approveAffiliation(
            @PathVariable Long affiliationId,
            @RequestBody Map<String, String> request) {
        String authorityDecision = request.get("authorityDecision");
        String notes = request.get("notes");
        ProviderAffiliationEntity affiliation = affiliationService.approveAffiliation(affiliationId, authorityDecision, notes);
        return ResponseEntity.ok(GenericResponse.success(
                "Affiliation approved",
                Map.of("affiliationId", affiliation.getId(), "status", affiliation.getStatus())
        ));
    }

    @PostMapping("/{affiliationId}/reject")
    public ResponseEntity<GenericResponse> rejectAffiliation(
            @PathVariable Long affiliationId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        ProviderAffiliationEntity affiliation = affiliationService.rejectAffiliation(affiliationId, reason);
        return ResponseEntity.ok(GenericResponse.success(
                "Affiliation rejected",
                Map.of("affiliationId", affiliation.getId(), "status", affiliation.getStatus())
        ));
    }

    @PostMapping("/{affiliationId}/terminate")
    public ResponseEntity<GenericResponse> terminateAffiliation(
            @PathVariable Long affiliationId,
            @RequestBody Map<String, Object> request) {
        LocalDate endDate = request.get("endDate") != null ?
                LocalDate.parse((String) request.get("endDate")) : null;
        String reason = (String) request.get("reason");
        ProviderAffiliationEntity affiliation = affiliationService.terminateAffiliation(affiliationId, endDate, reason);
        return ResponseEntity.ok(GenericResponse.success(
                "Affiliation terminated",
                Map.of("affiliationId", affiliation.getId(), "status", affiliation.getStatus())
        ));
    }

    @GetMapping("/{affiliationId}")
    public ResponseEntity<ProviderAffiliationEntity> getAffiliation(@PathVariable Long affiliationId) {
        return ResponseEntity.ok(affiliationService.getAffiliation(affiliationId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderAffiliationEntity>> getAffiliationsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(affiliationService.getAffiliationsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/active")
    public ResponseEntity<List<ProviderAffiliationEntity>> getActiveAffiliationsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(affiliationService.getActiveAffiliationsByProvider(providerId));
    }

    @GetMapping("/facility/{facilityId}/active")
    public ResponseEntity<List<ProviderAffiliationEntity>> getActiveAffiliationsByFacility(@PathVariable Long facilityId) {
        return ResponseEntity.ok(affiliationService.getActiveAffiliationsByFacility(facilityId));
    }

    @GetMapping("/provider/{providerId}/primary")
    public ResponseEntity<ProviderAffiliationEntity> getPrimaryAffiliation(@PathVariable Long providerId) {
        ProviderAffiliationEntity primary = affiliationService.getPrimaryAffiliation(providerId);
        return primary != null ? ResponseEntity.ok(primary) : ResponseEntity.notFound().build();
    }

    @GetMapping("/provider/{providerId}/facility/{facilityId}/active")
    public ResponseEntity<Map<String, Boolean>> checkActiveAffiliation(
            @PathVariable Long providerId,
            @PathVariable Long facilityId) {
        boolean hasActive = affiliationService.hasActiveAffiliation(providerId, facilityId);
        return ResponseEntity.ok(Map.of("hasActiveAffiliation", hasActive));
    }
}
