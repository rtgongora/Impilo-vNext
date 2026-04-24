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

    public GovernanceInternalController(OrganisationService organisationService,
                                        OrganisationUnitService organisationUnitService,
                                        JurisdictionAdminService jurisdictionAdminService,
                                        RoleCatalogService roleCatalogService,
                                        LinkageService linkageService,
                                        AssignmentService assignmentService,
                                        OnboardingWorkflowService onboardingWorkflowService,
                                        ScopeEvaluationService scopeEvaluationService,
                                        GovernanceSummaryService governanceSummaryService,
                                        MultiSiteGroupAdminService multiSiteGroupAdminService) {
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

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s);
    }
}
