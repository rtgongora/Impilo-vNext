package zw.gov.mohcc.impilo.indawo.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.indawo.api.dto.SiteRegulatoryDtos;
import zw.gov.mohcc.impilo.indawo.core.SiteRegulatoryService;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/site-registry")
public class SiteRegulatoryController {

    private final SiteRegulatoryService regulatoryService;

    public SiteRegulatoryController(SiteRegulatoryService regulatoryService) {
        this.regulatoryService = regulatoryService;
    }

    @GetMapping("/sites")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MOH_VIEWER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<PagedResponse<SiteRegulatoryDtos.SiteRegistrySummaryResponse>>> listSites(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "regulatory_status") String regulatoryStatus,
            @RequestParam(required = false, name = "site_category") String siteCategory,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RequestContext ctx = RequestContextHolder.require();
        Page<SiteRegulatoryDtos.SiteRegistrySummaryResponse> result = regulatoryService.searchSites(
                search, regulatoryStatus, siteCategory, province, district, page, size
        );
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements()),
                ctx.correlationId()));
    }

    @GetMapping("/sites/{siteId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MOH_VIEWER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> getSiteProfile(@PathVariable UUID siteId) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.getSiteProfile(siteId), ctx.correlationId()));
    }

    @PostMapping("/applications")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_APPLICANT','SITE_OPERATOR','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> createApplication(
            @Valid @RequestBody SiteRegulatoryDtos.CreateSiteApplicationRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.createApplication(request), ctx.correlationId()));
    }

    @PostMapping("/applications/{applicationId}/submit")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_APPLICANT','SITE_OPERATOR','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> submitApplication(
            @PathVariable UUID applicationId
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.submitApplication(applicationId), ctx.correlationId()));
    }

    @PostMapping("/sites/{siteId}/renewals")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_APPLICANT','SITE_OPERATOR','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> createRenewal(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "Applicant") String applicantName
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.createRenewalApplication(siteId, applicantName), ctx.correlationId()));
    }

    @GetMapping("/checklist-templates")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listChecklistTemplates(
            @RequestParam(name = "inspection_type") String inspectionType,
            @RequestParam(name = "site_category", required = false) String siteCategory
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.listChecklistTemplates(inspectionType, siteCategory), ctx.correlationId()));
    }

    @GetMapping("/checklist-templates/{templateId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChecklistTemplate(@PathVariable UUID templateId) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.getChecklistTemplate(templateId), ctx.correlationId()));
    }

    @PostMapping("/inspections")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.InspectionView>> scheduleInspection(
            @Valid @RequestBody SiteRegulatoryDtos.ScheduleSiteInspectionRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.scheduleInspection(request), ctx.correlationId()));
    }

    @PostMapping("/inspections/{inspectionId}/record")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.InspectionView>> recordInspection(
            @PathVariable UUID inspectionId,
            @Valid @RequestBody SiteRegulatoryDtos.RecordSiteInspectionOutcomeRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.recordInspectionOutcome(inspectionId, request), ctx.correlationId()));
    }

    @GetMapping("/inspections")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<SiteRegulatoryDtos.InspectionRegisterRow>>> listInspections(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.listInspectionRegister(status, size), ctx.correlationId()));
    }

    @PostMapping("/compliance-actions/{actionId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_OPERATOR','INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','REGULATORY_REVIEWER','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> updateComplianceAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody SiteRegulatoryDtos.UpdateComplianceActionRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.updateComplianceAction(actionId, request), ctx.correlationId()));
    }

    @GetMapping("/compliance-actions")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_OPERATOR','INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','REGULATORY_REVIEWER','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<SiteRegulatoryDtos.ComplianceActionRow>>> listComplianceActions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.listComplianceActions(status, size), ctx.correlationId()));
    }

    @PostMapping("/licences")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.LicenceView>> issueLicence(
            @Valid @RequestBody SiteRegulatoryDtos.IssueLicenceRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.issueLicence(request), ctx.correlationId()));
    }

    @PostMapping("/enforcement-cases")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','REGULATORY_REVIEWER','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.EnforcementCaseView>> openEnforcementCase(
            @Valid @RequestBody SiteRegulatoryDtos.OpenEnforcementCaseRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.openEnforcementCase(request), ctx.correlationId()));
    }

    @GetMapping("/enforcement-cases")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('INSPECTOR','ENVIRONMENTAL_HEALTH_OFFICER','REGULATORY_REVIEWER','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<SiteRegulatoryDtos.EnforcementCaseRow>>> listEnforcementCases(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int size
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.listEnforcementCases(status, size), ctx.correlationId()));
    }

    // ── Documents (upload + verification) ─────────────────────────

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SITE_APPLICANT','SITE_OPERATOR','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart("siteId") UUID siteId,
            @RequestPart("documentType") String documentType,
            @RequestPart(value = "applicationId", required = false) UUID applicationId,
            @RequestPart(value = "notes", required = false) String notes
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(regulatoryService.uploadDocument(file, siteId, applicationId, documentType, notes), ctx.correlationId()));
    }

    @PostMapping("/documents/{documentId}/verify")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('REGULATORY_REVIEWER','LICENSING_OFFICER','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.SiteRegulatoryProfileResponse>> verifyDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody SiteRegulatoryDtos.VerifyDocumentRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.verifyDocument(documentId, request), ctx.correlationId()));
    }

    // ── Assignments / routing ─────────────────────────────────────

    @PostMapping("/assignments")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('REGISTRY_ADMIN','REGULATORY_REVIEWER','LICENSING_OFFICER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.AssignmentView>> createAssignment(
            @Valid @RequestBody SiteRegulatoryDtos.CreateAssignmentRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(regulatoryService.createAssignment(request), ctx.correlationId()));
    }

    @PostMapping("/assignments/{assignmentId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('REGISTRY_ADMIN','REGULATORY_REVIEWER','LICENSING_OFFICER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<SiteRegulatoryDtos.AssignmentView>> updateAssignment(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SiteRegulatoryDtos.UpdateAssignmentRequest request
    ) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.updateAssignment(assignmentId, request), ctx.correlationId()));
    }

    @GetMapping("/dashboard/summary")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MOH_VIEWER','PUBLIC_HEALTH_OFFICER','ENV_HEALTH','REGISTRY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboardSummary() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(regulatoryService.getDashboardSummary(), ctx.correlationId()));
    }
}

