package zw.gov.mohcc.impilo.governance.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.governance.core.*;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/governance")
public class GovernanceInternalController {

    private final OrganisationService organisationService;
    private final OrganisationUnitService organisationUnitService;
    private final JurisdictionAdminService jurisdictionAdminService;
    private final RoleCatalogService roleCatalogService;
    private final LinkageService linkageService;
    private final AssignmentService assignmentService;
    private final OnboardingWorkflowService onboardingWorkflowService;
    private final ScopeEvaluationService scopeEvaluationService;
    private final GovernanceSummaryService governanceSummaryService;
    private final MultiSiteGroupAdminService multiSiteGroupAdminService;
    private final AccessRequestService accessRequestService;
    private final HscEmploymentService hscEmploymentService;
    private final OrganisationMembershipService organisationMembershipService;
    private final BootstrapGovernanceService bootstrapGovernanceService;
    private final AuthorisedRepresentativeService authorisedRepresentativeService;
    private final ImportBatchService importBatchService;

    public GovernanceInternalController(OrganisationService organisationService,
                                        OrganisationUnitService organisationUnitService,
                                        JurisdictionAdminService jurisdictionAdminService,
                                        RoleCatalogService roleCatalogService,
                                        LinkageService linkageService,
                                        AssignmentService assignmentService,
                                        OnboardingWorkflowService onboardingWorkflowService,
                                        ScopeEvaluationService scopeEvaluationService,
                                        GovernanceSummaryService governanceSummaryService,
                                        MultiSiteGroupAdminService multiSiteGroupAdminService,
                                        AccessRequestService accessRequestService,
                                        HscEmploymentService hscEmploymentService,
                                        OrganisationMembershipService organisationMembershipService,
                                        BootstrapGovernanceService bootstrapGovernanceService,
                                        AuthorisedRepresentativeService authorisedRepresentativeService,
                                        ImportBatchService importBatchService) {
        this.organisationService = organisationService;
        this.organisationUnitService = organisationUnitService;
        this.jurisdictionAdminService = jurisdictionAdminService;
        this.roleCatalogService = roleCatalogService;
        this.linkageService = linkageService;
        this.assignmentService = assignmentService;
        this.onboardingWorkflowService = onboardingWorkflowService;
        this.scopeEvaluationService = scopeEvaluationService;
        this.governanceSummaryService = governanceSummaryService;
        this.multiSiteGroupAdminService = multiSiteGroupAdminService;
        this.accessRequestService = accessRequestService;
        this.hscEmploymentService = hscEmploymentService;
        this.organisationMembershipService = organisationMembershipService;
        this.bootstrapGovernanceService = bootstrapGovernanceService;
        this.authorisedRepresentativeService = authorisedRepresentativeService;
        this.importBatchService = importBatchService;
    }

    private String corr() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : "";
    }

    private UUID tenant() {
        UUID t = TrustContextHolder.require().tenantId();
        if (t == null) {
            throw new IllegalArgumentException("Missing X-Tenant-ID trust header");
        }
        return t;
    }

    @PostMapping("/organisations")
    public ResponseEntity<ApiResponse<OrganisationEntity>> createOrganisation(@Valid @RequestBody GovernanceDtos.CreateOrganisationRequest req) {
        OrganisationEntity o = organisationService.createOrganisation(
                tenant(), req.organisationCode(), req.name(), req.legalName(), req.organisationType(),
                req.parentOrganisationId(), req.metadataJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(o, corr()));
    }

    @GetMapping("/organisations")
    public ResponseEntity<ApiResponse<List<OrganisationEntity>>> listOrganisations() {
        return ResponseEntity.ok(ApiResponse.ok(organisationService.list(tenant()), corr()));
    }

    @GetMapping("/organisations/{id}")
    public ResponseEntity<ApiResponse<OrganisationEntity>> getOrganisation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organisationService.get(id), corr()));
    }

    @PatchMapping("/organisations/{id}/status")
    public ResponseEntity<ApiResponse<OrganisationEntity>> orgStatus(@PathVariable UUID id,
                                                                       @Valid @RequestBody GovernanceDtos.OrganisationStatusRequest req) {
        GovernanceEnums.OrganisationStatus st = GovernanceEnums.OrganisationStatus.valueOf(req.status());
        return ResponseEntity.ok(ApiResponse.ok(organisationService.updateStatus(id, st), corr()));
    }

    @PatchMapping("/organisations/{id}")
    public ResponseEntity<ApiResponse<OrganisationEntity>> patchOrganisation(@PathVariable UUID id,
                                                                              @RequestBody GovernanceDtos.PatchOrganisationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                organisationService.patchOrganisation(id, req.name(), req.legalName(), req.metadataJson()), corr()));
    }

    @GetMapping("/organisations/{id}/users")
    public ResponseEntity<ApiResponse<List<OrganisationMembershipEntity>>> listOrgUsers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organisationMembershipService.list(tenant(), id), corr()));
    }

    @PostMapping("/organisations/{id}/users")
    public ResponseEntity<ApiResponse<OrganisationMembershipEntity>> addOrgUser(@PathVariable UUID id,
                                                                                 @Valid @RequestBody GovernanceDtos.OrganisationMemberRequest req) {
        OrganisationMembershipEntity member = organisationMembershipService.addMember(tenant(), id, Map.of(
                "userId", req.userId(),
                "subjectType", req.subjectType() != null ? req.subjectType() : "USER",
                "roleTemplate", req.roleTemplate() != null ? req.roleTemplate() : "",
                "status", req.status() != null ? req.status() : "ACTIVE",
                "metadata", req.metadata() != null ? req.metadata() : ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(member, corr()));
    }

    @PatchMapping("/organisations/{id}/users/{userId}")
    public ResponseEntity<ApiResponse<OrganisationMembershipEntity>> patchOrgUser(@PathVariable UUID id,
                                                                                   @PathVariable String userId,
                                                                                   @RequestBody GovernanceDtos.OrganisationMemberRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                organisationMembershipService.updateMember(tenant(), id, userId, Map.of(
                        "subjectType", req.subjectType() != null ? req.subjectType() : "USER",
                        "roleTemplate", req.roleTemplate() != null ? req.roleTemplate() : "",
                        "status", req.status() != null ? req.status() : "ACTIVE",
                        "metadata", req.metadata() != null ? req.metadata() : "")), corr()));
    }

    @PostMapping("/organisations/{id}/users/{userId}/suspend")
    public ResponseEntity<ApiResponse<OrganisationMembershipEntity>> suspendOrgUser(@PathVariable UUID id, @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(organisationMembershipService.suspendMember(tenant(), id, userId), corr()));
    }

    @PostMapping("/organisations/{id}/users/{userId}/offboard")
    public ResponseEntity<ApiResponse<OrganisationMembershipEntity>> offboardOrgUser(@PathVariable UUID id, @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.ok(organisationMembershipService.offboardMember(tenant(), id, userId), corr()));
    }

    @PostMapping("/access-requests")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> createAccessRequest(@Valid @RequestBody GovernanceDtos.CreateAccessRequestBody req) {
        AccessRequestEntity entity = accessRequestService.create(tenant(), Map.of(
                "requestType", req.requestType(),
                "status", req.status() != null ? req.status() : "PENDING",
                "requesterId", req.requesterId(),
                "requesterName", req.requesterName() != null ? req.requesterName() : req.requesterId(),
                "organisationId", req.organisationId() != null ? req.organisationId().toString() : "",
                "targetSubjectId", req.targetSubjectId() != null ? req.targetSubjectId() : "",
                "requestedRole", req.requestedRole() != null ? req.requestedRole() : "",
                "requestedScope", req.requestedScope() != null ? req.requestedScope() : "",
                "requestedEnvironment", req.requestedEnvironment() != null ? req.requestedEnvironment() : "",
                "requestedDataScope", req.requestedDataScope() != null ? req.requestedDataScope() : "",
                "riskLevel", req.riskLevel() != null ? req.riskLevel() : "",
                "policyPrecheckResult", req.policyPrecheckResult() != null ? req.policyPrecheckResult() : "",
                "approvalsRequired", req.approvalsRequired() != null ? req.approvalsRequired() : List.of(),
                "payload", req.payload() != null ? req.payload() : Map.of(),
                "fallbackRequestId", req.fallbackRequestId() != null ? req.fallbackRequestId() : ""));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(entity, corr()));
    }

    @GetMapping("/access-requests")
    public ResponseEntity<ApiResponse<List<AccessRequestEntity>>> listAccessRequests() {
        return ResponseEntity.ok(ApiResponse.ok(accessRequestService.list(tenant()), corr()));
    }

    @PostMapping("/access-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> approveAccessRequest(@PathVariable UUID requestId,
                                                                                  @RequestBody(required = false) GovernanceDtos.AccessRequestDecisionBody body) {
        return transitionAccessRequest(requestId, "APPROVED", body);
    }

    @PostMapping("/access-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> rejectAccessRequest(@PathVariable UUID requestId,
                                                                                @RequestBody(required = false) GovernanceDtos.AccessRequestDecisionBody body) {
        return transitionAccessRequest(requestId, "REJECTED", body);
    }

    @PostMapping("/access-requests/{requestId}/revoke")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> revokeAccessRequest(@PathVariable UUID requestId,
                                                                               @RequestBody(required = false) GovernanceDtos.AccessRequestDecisionBody body) {
        return transitionAccessRequest(requestId, "REVOKED", body);
    }

    @PostMapping("/access-requests/{requestId}/escalate")
    public ResponseEntity<ApiResponse<AccessRequestEntity>> escalateAccessRequest(@PathVariable UUID requestId,
                                                                                   @RequestBody(required = false) GovernanceDtos.AccessRequestDecisionBody body) {
        return transitionAccessRequest(requestId, "ESCALATED", body);
    }

    @GetMapping("/hsc/employment-records/search")
    public ResponseEntity<ApiResponse<List<HscEmploymentEntity>>> searchHscEmployment(
            @RequestParam(required = false) String providerWorkerId,
            @RequestParam(required = false) String healthId,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(
                hscEmploymentService.search(tenant(), providerWorkerId, healthId, facilityId, province, district, status), corr()));
    }

    @GetMapping("/hsc/employment-records/{employmentRecordId}")
    public ResponseEntity<ApiResponse<HscEmploymentEntity>> getHscEmployment(@PathVariable UUID employmentRecordId) {
        return ResponseEntity.ok(ApiResponse.ok(hscEmploymentService.get(employmentRecordId), corr()));
    }

    @PostMapping("/hsc/employment-records")
    public ResponseEntity<ApiResponse<HscEmploymentEntity>> upsertHscEmployment(@Valid @RequestBody GovernanceDtos.UpsertHscEmploymentRequest req) {
        HscEmploymentEntity entity = hscEmploymentService.upsert(tenant(), Map.of(
                "employmentRecordId", req.employmentRecordId() != null ? req.employmentRecordId() : "",
                "providerWorkerId", req.providerWorkerId() != null ? req.providerWorkerId() : "",
                "linkedHealthId", req.linkedHealthId() != null ? req.linkedHealthId() : "",
                "employerOrganisationId", req.employerOrganisationId() != null ? req.employerOrganisationId().toString() : "",
                "employmentStatus", req.employmentStatus() != null ? req.employmentStatus() : "ACTIVE",
                "postId", req.postId() != null ? req.postId() : "",
                "postTitle", req.postTitle() != null ? req.postTitle() : "",
                "grade", req.grade() != null ? req.grade() : "",
                "cadre", req.cadre() != null ? req.cadre() : "",
                "establishmentUnit", req.establishmentUnit() != null ? req.establishmentUnit() : "",
                "currentPostingProvince", req.currentPostingProvince() != null ? req.currentPostingProvince() : "",
                "currentPostingDistrict", req.currentPostingDistrict() != null ? req.currentPostingDistrict() : "",
                "currentPostingFacility", req.currentPostingFacility() != null ? req.currentPostingFacility() : "",
                "currentPostingDepartment", req.currentPostingDepartment() != null ? req.currentPostingDepartment() : "",
                "transferStatus", req.transferStatus() != null ? req.transferStatus() : "",
                "promotionStatus", req.promotionStatus() != null ? req.promotionStatus() : "",
                "disciplinaryEmploymentStatus", req.disciplinaryEmploymentStatus() != null ? req.disciplinaryEmploymentStatus() : "",
                "verificationStatus", req.verificationStatus() != null ? req.verificationStatus() : "PENDING"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(entity, corr()));
    }

    @PostMapping("/org-units")
    public ResponseEntity<ApiResponse<OrganisationUnitEntity>> createUnit(@Valid @RequestBody GovernanceDtos.CreateOrgUnitRequest req) {
        OrganisationUnitEntity u = organisationUnitService.createUnit(
                tenant(), req.organisationId(), req.parentUnitId(), req.unitCode(), req.name(), req.unitType());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(u, corr()));
    }

    @GetMapping("/org-units")
    public ResponseEntity<ApiResponse<List<OrganisationUnitEntity>>> listUnits(@RequestParam UUID organisationId) {
        return ResponseEntity.ok(ApiResponse.ok(organisationUnitService.listUnits(tenant(), organisationId), corr()));
    }

    @PostMapping("/jurisdictions")
    public ResponseEntity<ApiResponse<JurisdictionEntity>> createJurisdiction(@Valid @RequestBody GovernanceDtos.CreateJurisdictionRequest req) {
        JurisdictionEntity j = jurisdictionAdminService.createJurisdiction(
                tenant(), req.jurisdictionCode(), req.name(),
                GovernanceEnums.JurisdictionType.valueOf(req.jurisdictionType()),
                req.parentJurisdictionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(j, corr()));
    }

    @GetMapping("/jurisdictions")
    public ResponseEntity<ApiResponse<List<JurisdictionEntity>>> listJurisdictions() {
        return ResponseEntity.ok(ApiResponse.ok(jurisdictionAdminService.list(tenant()), corr()));
    }

    @PostMapping("/jurisdiction-links")
    public ResponseEntity<ApiResponse<JurisdictionLinkEntity>> createJurisdictionLink(@Valid @RequestBody GovernanceDtos.CreateJurisdictionLinkRequest req) {
        JurisdictionLinkEntity link = jurisdictionAdminService.linkTarget(
                tenant(),
                GovernanceEnums.AssignmentTargetType.valueOf(req.targetType()),
                req.targetId(),
                req.jurisdictionId(),
                req.relationshipType(),
                req.primary(),
                parseDate(req.startDate()),
                parseDate(req.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(link, corr()));
    }

    @PostMapping("/role-definitions")
    public ResponseEntity<ApiResponse<RoleDefinitionEntity>> createRole(@Valid @RequestBody GovernanceDtos.CreateRoleDefinitionRequest req) {
        List<GovernanceEnums.AssignmentTargetType> allowed = req.allowedTargetTypes().stream()
                .map(GovernanceEnums.AssignmentTargetType::valueOf)
                .toList();
        RoleDefinitionEntity r = roleCatalogService.createRoleDefinition(
                tenant(),
                req.roleCode(),
                req.name(),
                req.description(),
                GovernanceEnums.RoleCategory.valueOf(req.roleCategory()),
                GovernanceEnums.RoleLevel.valueOf(req.roleLevel()),
                allowed,
                req.requiresProvider(),
                req.requiresProfessionalStanding(),
                req.specialGovernance());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(r, corr()));
    }

    @PostMapping("/facility-links")
    public ResponseEntity<ApiResponse<FacilityOrganisationLinkEntity>> facilityLink(@Valid @RequestBody GovernanceDtos.CreateFacilityLinkRequest req) {
        FacilityOrganisationLinkEntity link = linkageService.linkFacility(
                tenant(),
                req.facilityId(),
                req.organisationId(),
                req.organisationUnitId(),
                req.relationshipType(),
                req.primary(),
                parseDate(req.startDate()),
                parseDate(req.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(link, corr()));
    }

    @PostMapping("/site-links")
    public ResponseEntity<ApiResponse<SiteOrganisationLinkEntity>> siteLink(@Valid @RequestBody GovernanceDtos.CreateSiteLinkRequest req) {
        SiteOrganisationLinkEntity link = linkageService.linkSite(
                tenant(),
                req.siteId(),
                req.organisationId(),
                req.organisationUnitId(),
                req.relationshipType(),
                req.primary(),
                parseDate(req.startDate()),
                parseDate(req.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(link, corr()));
    }

    @PostMapping("/multi-site-groups")
    public ResponseEntity<ApiResponse<MultiSiteGroupEntity>> createGroup(@Valid @RequestBody GovernanceDtos.CreateMultiSiteGroupRequest req) {
        MultiSiteGroupEntity g = multiSiteGroupAdminService.createGroup(
                tenant(), req.code(), req.name(), req.groupType(), req.organisationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(g, corr()));
    }

    @PostMapping("/multi-site-groups/{id}/members")
    public ResponseEntity<ApiResponse<MultiSiteGroupMemberEntity>> addMember(@PathVariable UUID id,
                                                                              @Valid @RequestBody GovernanceDtos.AddGroupMemberRequest req) {
        MultiSiteGroupMemberEntity m = multiSiteGroupAdminService.addMember(
                tenant(),
                id,
                GovernanceEnums.AssignmentTargetType.valueOf(req.memberTargetType()),
                req.memberTargetId(),
                parseDate(req.startDate()),
                parseDate(req.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(m, corr()));
    }

    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<AssignmentEntity>> createAssignment(@Valid @RequestBody GovernanceDtos.CreateAssignmentRequest req) {
        AssignmentEntity a = assignmentService.createAssignment(
                tenant(),
                GovernanceEnums.AssignmentSubjectType.valueOf(req.subjectType()),
                req.subjectId(),
                req.roleDefinitionId(),
                GovernanceEnums.AssignmentTargetType.valueOf(req.targetType()),
                req.targetId(),
                req.organisationId(),
                req.organisationUnitId(),
                req.jurisdictionId(),
                GovernanceEnums.AssignmentStatus.valueOf(req.initialStatus()),
                req.primary(),
                req.secondary(),
                req.scopeSummary(),
                req.authorityLevel(),
                req.reportingLineRef(),
                req.notes(),
                req.extensionJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(a, corr()));
    }

    @PostMapping("/assignments/{id}/transition")
    public ResponseEntity<ApiResponse<AssignmentEntity>> transition(@PathVariable UUID id,
                                                                     @Valid @RequestBody GovernanceDtos.AssignmentTransitionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.transition(id, GovernanceEnums.AssignmentStatus.valueOf(req.newStatus()), req.reason()),
                corr()));
    }

    @GetMapping("/assignments/search")
    public ResponseEntity<ApiResponse<List<AssignmentEntity>>> searchAssignments(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.search(tenant(), subjectType, subjectId, status), corr()));
    }

    @GetMapping("/assignments/{id}/history")
    public ResponseEntity<ApiResponse<List<AssignmentStatusHistoryEntity>>> assignmentHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.assignmentHistory(id), corr()));
    }

    @PostMapping("/onboarding/single-facility")
    public ResponseEntity<ApiResponse<OnboardingWorkflowService.OnboardingResult>> onboardSingle(
            @Valid @RequestBody GovernanceDtos.SingleFacilityOnboardRequest req) {
        OnboardingWorkflowService.OnboardingResult r = onboardingWorkflowService.onboardSingleFacilityOrganisation(
                tenant(),
                req.organisationCode(),
                req.organisationName(),
                req.legalName(),
                req.organisationType(),
                req.facilityId(),
                req.createDefaultUnit());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(r, corr()));
    }

    @PostMapping("/onboarding/multi-facility")
    public ResponseEntity<ApiResponse<OnboardingWorkflowService.OnboardingResult>> onboardMulti(
            @Valid @RequestBody GovernanceDtos.MultiFacilityOnboardRequest req) {
        OnboardingWorkflowService.OnboardingResult r = onboardingWorkflowService.onboardMultiFacilityOrganisation(
                tenant(),
                req.organisationCode(),
                req.organisationName(),
                req.legalName(),
                req.organisationType(),
                req.facilityIds(),
                req.createDefaultUnit());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(r, corr()));
    }

    @PostMapping("/scope/evaluate-facility")
    public ResponseEntity<ApiResponse<ScopeEvaluationService.ScopeFacilityResult>> evaluateFacility(
            @Valid @RequestBody GovernanceDtos.EvaluateFacilityScopeRequest req) {
        ScopeEvaluationService.ScopeFacilityResult r = scopeEvaluationService.evaluateFacilityScope(
                tenant(), req.actorHealthId(), req.providerPublicId(), req.tusoFacilityId());
        return ResponseEntity.ok(ApiResponse.ok(r, corr()));
    }

    @GetMapping("/summaries/organisation/{id}")
    public ResponseEntity<ApiResponse<GovernanceSummaryService.OrganisationSummary>> orgSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(governanceSummaryService.organisationSummary(id), corr()));
    }

    @GetMapping("/summaries/facility/{facilityId}")
    public ResponseEntity<ApiResponse<GovernanceSummaryService.FacilityGovernanceSummary>> facilitySummary(
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.ok(
                governanceSummaryService.facilityGovernanceSummary(tenant(), facilityId), corr()));
    }

    @GetMapping("/bootstrap/state")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bootstrapState() {
        return ResponseEntity.ok(ApiResponse.ok(bootstrapGovernanceService.state(tenant()), corr()));
    }

    @PostMapping("/bootstrap/accounts")
    public ResponseEntity<ApiResponse<BootstrapAccountEntity>> createBootstrapAccount(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bootstrapGovernanceService.createAccount(tenant(), body), corr()));
    }

    @PostMapping("/bootstrap/accounts/{accountId}/activate")
    public ResponseEntity<ApiResponse<BootstrapAccountEntity>> activateBootstrapAccount(@PathVariable UUID accountId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(bootstrapGovernanceService.activate(accountId, body), corr()));
    }

    @PostMapping("/bootstrap/close")
    public ResponseEntity<ApiResponse<BootstrapStateEntity>> closeBootstrap(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(bootstrapGovernanceService.close(tenant()), corr()));
    }

    @PostMapping("/bootstrap/recovery/open")
    public ResponseEntity<ApiResponse<BootstrapStateEntity>> openBootstrapRecovery(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(bootstrapGovernanceService.openRecovery(tenant(), String.valueOf(body.get("ceremonyReference"))), corr()));
    }

    @PostMapping("/bootstrap/recovery/close")
    public ResponseEntity<ApiResponse<BootstrapStateEntity>> closeBootstrapRecovery() {
        return ResponseEntity.ok(ApiResponse.ok(bootstrapGovernanceService.closeRecovery(tenant()), corr()));
    }

    @GetMapping("/organisations/{id}/authorised-representatives")
    public ResponseEntity<ApiResponse<List<AuthorisedRepresentativeEntity>>> listAuthorisedRepresentatives(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(authorisedRepresentativeService.list(tenant(), id), corr()));
    }

    @PostMapping("/organisations/{id}/authorised-representatives")
    public ResponseEntity<ApiResponse<AuthorisedRepresentativeEntity>> inviteAuthorisedRepresentative(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authorisedRepresentativeService.invite(tenant(), id, body), corr()));
    }

    @PatchMapping("/organisations/{orgId}/authorised-representatives/{repId}")
    public ResponseEntity<ApiResponse<AuthorisedRepresentativeEntity>> patchAuthorisedRepresentative(@PathVariable UUID repId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(authorisedRepresentativeService.update(repId, body), corr()));
    }

    @PostMapping("/organisations/{orgId}/authorised-representatives/{repId}/suspend")
    public ResponseEntity<ApiResponse<AuthorisedRepresentativeEntity>> suspendAuthorisedRepresentative(@PathVariable UUID repId) {
        return ResponseEntity.ok(ApiResponse.ok(authorisedRepresentativeService.update(repId, Map.of("status", "SUSPENDED")), corr()));
    }

    @PostMapping("/organisations/{orgId}/authorised-representatives/{repId}/revoke")
    public ResponseEntity<ApiResponse<AuthorisedRepresentativeEntity>> revokeAuthorisedRepresentative(@PathVariable UUID repId) {
        return ResponseEntity.ok(ApiResponse.ok(authorisedRepresentativeService.update(repId, Map.of("status", "REVOKED")), corr()));
    }

    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<ImportBatchEntity>> uploadImport(@RequestBody Map<String, Object> body) {
        ImportBatchEntity batch = importBatchService.upload(
                tenant(),
                UUID.fromString(String.valueOf(body.get("organisationId"))),
                String.valueOf(body.get("uploadedByUserId")),
                String.valueOf(body.get("importType")),
                String.valueOf(body.get("sourceFileName")),
                String.valueOf(body.get("csvContent")));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(batch, corr()));
    }

    @GetMapping("/imports")
    public ResponseEntity<ApiResponse<List<ImportBatchEntity>>> listImports(@RequestParam UUID organisationId) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.list(tenant(), organisationId), corr()));
    }

    @GetMapping("/imports/{importBatchId}")
    public ResponseEntity<ApiResponse<ImportBatchEntity>> getImport(@PathVariable UUID importBatchId) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.get(importBatchId), corr()));
    }

    @GetMapping("/imports/{importBatchId}/rows")
    public ResponseEntity<ApiResponse<List<ImportRowEntity>>> importRows(@PathVariable UUID importBatchId) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.rows(importBatchId), corr()));
    }

    @GetMapping("/imports/{importBatchId}/exceptions")
    public ResponseEntity<ApiResponse<List<ImportExceptionEntity>>> importExceptions(@PathVariable UUID importBatchId) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.exceptions(importBatchId), corr()));
    }

    @PostMapping("/imports/{importBatchId}/approve")
    public ResponseEntity<ApiResponse<ImportBatchEntity>> approveImport(@PathVariable UUID importBatchId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.approve(importBatchId, String.valueOf(body.get("actorId"))), corr()));
    }

    @PostMapping("/imports/{importBatchId}/send-invitations")
    public ResponseEntity<ApiResponse<ImportBatchEntity>> sendImportInvitations(@PathVariable UUID importBatchId) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.sendInvitations(importBatchId), corr()));
    }

    @GetMapping("/imports/templates/{importType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importTemplate(@PathVariable String importType) {
        return ResponseEntity.ok(ApiResponse.ok(importBatchService.template(importType), corr()));
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s);
    }

    private ResponseEntity<ApiResponse<AccessRequestEntity>> transitionAccessRequest(
            UUID requestId, String status, GovernanceDtos.AccessRequestDecisionBody body) {
        TrustContext ctx = TrustContextHolder.get();
        String actorId = body != null && body.actorId() != null ? body.actorId()
                : ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
        String notes = body != null ? body.notes() : null;
        return ResponseEntity.ok(ApiResponse.ok(accessRequestService.transition(requestId, status, notes, actorId), corr()));
    }
}
