package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityRegulatoryDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilityRegulatoryService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionType;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/facility-registry")
public class FacilityRegulatoryController {

    private final FacilityRegulatoryService regulatoryService;

    public FacilityRegulatoryController(FacilityRegulatoryService regulatoryService) {
        this.regulatoryService = regulatoryService;
    }

    @GetMapping("/facilities")
    public ResponseEntity<ApiResponse<PagedResponse<FacilityRegulatoryDtos.FacilityRegistrySummaryResponse>>> listFacilities(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) FacilityRegulatoryStatus regulatoryStatus,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<FacilityRegulatoryDtos.FacilityRegistrySummaryResponse> result = regulatoryService.searchFacilities(
                search, regulatoryStatus, facilityType, province, district, page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements()),
                ctx.correlationId().toString()));
    }

    @GetMapping("/facilities/{facilityId}")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse>> getFacilityProfile(
            @PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.getFacilityProfile(facilityId), ctx.correlationId().toString()));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse>> createApplication(
            @Valid @RequestBody FacilityRegulatoryDtos.CreateFacilityApplicationRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.createApplication(request), ctx.correlationId().toString()));
    }

    @PostMapping("/applications/{applicationId}/submit")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse>> submitApplication(
            @PathVariable UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.submitApplication(applicationId), ctx.correlationId().toString()));
    }

    @PostMapping("/applications/{applicationId}/ready-for-inspection")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse>> markReadyForInspection(
            @PathVariable UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.markReadyForInspection(applicationId), ctx.correlationId().toString()));
    }

    @PostMapping("/documents")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.DocumentView>> uploadDocument(
            @Valid @RequestBody FacilityRegulatoryDtos.UploadFacilityDocumentRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.uploadDocument(request), ctx.correlationId().toString()));
    }

    @GetMapping("/checklist-templates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listChecklistTemplates(
            @RequestParam FacilityInspectionType inspectionType,
            @RequestParam(required = false) String facilityType) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                regulatoryService.listChecklistTemplates(inspectionType, facilityType),
                ctx.correlationId().toString()));
    }

    @PostMapping("/inspections")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.InspectionView>> scheduleInspection(
            @Valid @RequestBody FacilityRegulatoryDtos.ScheduleFacilityInspectionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.scheduleInspection(request), ctx.correlationId().toString()));
    }

    @PostMapping("/inspections/{inspectionId}/record")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.InspectionView>> recordInspection(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody FacilityRegulatoryDtos.RecordInspectionOutcomeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.recordInspectionOutcome(inspectionId, request), ctx.correlationId().toString()));
    }

    @PostMapping("/compliance-actions/{actionId}")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.FacilityRegulatoryProfileResponse>> updateComplianceAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody FacilityRegulatoryDtos.UpdateComplianceActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.updateComplianceAction(actionId, request), ctx.correlationId().toString()));
    }

    @PostMapping("/committee-reviews")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.CommitteeReviewView>> recordCommitteeDecision(
            @Valid @RequestBody FacilityRegulatoryDtos.RecordCommitteeDecisionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.recordCommitteeDecision(request), ctx.correlationId().toString()));
    }

    @PostMapping("/enforcement-cases")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.EnforcementCaseView>> openEnforcementCase(
            @Valid @RequestBody FacilityRegulatoryDtos.OpenEnforcementCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.openEnforcementCase(request), ctx.correlationId().toString()));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<ApiResponse<FacilityRegulatoryDtos.DashboardSummaryResponse>> dashboardSummary() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.getDashboardSummary(), ctx.correlationId().toString()));
    }
}
