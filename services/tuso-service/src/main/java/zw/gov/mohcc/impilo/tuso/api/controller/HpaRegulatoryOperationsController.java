package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.ApplicationGovernanceService;
import zw.gov.mohcc.impilo.tuso.core.InspectionContentService;
import zw.gov.mohcc.impilo.tuso.core.InspectionVisitService;
import zw.gov.mohcc.impilo.tuso.core.PicNominationService;
import zw.gov.mohcc.impilo.tuso.core.PremisesService;
import zw.gov.mohcc.impilo.tuso.core.RegulatoryDemandSignalService;
import zw.gov.mohcc.impilo.tuso.core.RegulatoryRuleService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ApplicationInformationRequestEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ExternalCouncilReviewEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesOccupancyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClassificationTemplateApplicabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionRequirementModuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionResponseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTemplateCompositionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionVisitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityInspectionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HPA regulatory operations surface (V018): catalogues, rules, premises,
 * RFI cycle, external council reviews, PIC nominations, inspection visits and
 * structured responses. Companion to {@link FacilityRegulatoryController}.
 */
@RestController
@RequestMapping("/v1/internal/facility-registry")
public class HpaRegulatoryOperationsController {

    private final ApplicationGovernanceService governanceService;
    private final PicNominationService picNominationService;
    private final InspectionVisitService visitService;
    private final PremisesService premisesService;
    private final RegulatoryRuleService ruleService;
    private final FacilityClassificationRepository classificationRepository;
    private final InspectionContentService contentService;
    private final FacilityInspectionRepository inspectionRepository;
    private final RegulatoryDemandSignalService demandSignalService;

    public HpaRegulatoryOperationsController(ApplicationGovernanceService governanceService,
                                             PicNominationService picNominationService,
                                             InspectionVisitService visitService,
                                             PremisesService premisesService,
                                             RegulatoryRuleService ruleService,
                                             FacilityClassificationRepository classificationRepository,
                                             InspectionContentService contentService,
                                             FacilityInspectionRepository inspectionRepository,
                                             RegulatoryDemandSignalService demandSignalService) {
        this.governanceService = governanceService;
        this.picNominationService = picNominationService;
        this.visitService = visitService;
        this.premisesService = premisesService;
        this.ruleService = ruleService;
        this.classificationRepository = classificationRepository;
        this.contentService = contentService;
        this.inspectionRepository = inspectionRepository;
        this.demandSignalService = demandSignalService;
    }

    // ---- Catalogues + rules ------------------------------------------------

    @GetMapping("/application-types")
    public ResponseEntity<ApiResponse<List<RegulatoryApplicationTypeEntity>>> applicationTypes() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.listApplicationTypes(), corr(ctx)));
    }

    @GetMapping("/classifications")
    public ResponseEntity<ApiResponse<List<FacilityClassificationEntity>>> classifications() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                classificationRepository.findByActiveTrueOrderByClassCodeAscFacilityTypeLabelAsc(), corr(ctx)));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<RegulatoryRuleEntity>>> rules() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(ruleService.listRules(), corr(ctx)));
    }

    @PostMapping("/rules/{ruleId}/approve")
    public ResponseEntity<ApiResponse<RegulatoryRuleEntity>> approveRule(@PathVariable UUID ruleId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(ruleService.approveRule(ruleId), corr(ctx)));
    }

    // ---- Premises + shared occupancy ---------------------------------------

    @GetMapping("/premises")
    public ResponseEntity<ApiResponse<List<FacilityPremisesEntity>>> listPremises() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(premisesService.listPremises(), corr(ctx)));
    }

    @PostMapping("/premises")
    public ResponseEntity<ApiResponse<FacilityPremisesEntity>> createPremises(
            @RequestBody PremisesService.CreatePremisesRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(premisesService.createPremises(request), corr(ctx)));
    }

    public record OccupancyRequest(Long facilityId, String role, UUID relocationApplicationId, String notes) {}

    @PostMapping("/premises/{premisesId}/occupancies")
    public ResponseEntity<ApiResponse<FacilityPremisesOccupancyEntity>> assignOccupancy(
            @PathVariable UUID premisesId, @RequestBody OccupancyRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                premisesService.assignOccupancy(premisesId, request.facilityId(), request.role(),
                        request.relocationApplicationId(), request.notes()), corr(ctx)));
    }

    @GetMapping("/premises/{premisesId}/occupancies")
    public ResponseEntity<ApiResponse<List<FacilityPremisesOccupancyEntity>>> occupants(@PathVariable UUID premisesId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(premisesService.listOccupants(premisesId), corr(ctx)));
    }

    @GetMapping("/facilities/{facilityId}/occupancy-history")
    public ResponseEntity<ApiResponse<List<FacilityPremisesOccupancyEntity>>> occupancyHistory(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(premisesService.occupancyHistory(facilityId), corr(ctx)));
    }

    // ---- RFI cycle ----------------------------------------------------------

    public record OpenRfiRequest(String message, LocalDate dueDate) {}
    public record RespondRfiRequest(String responseNotes, UUID responseDocumentId) {}

    @PostMapping("/applications/{applicationId}/information-requests")
    public ResponseEntity<ApiResponse<ApplicationInformationRequestEntity>> openRfi(
            @PathVariable UUID applicationId, @RequestBody OpenRfiRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                governanceService.openInformationRequest(applicationId, request.message(), request.dueDate()), corr(ctx)));
    }

    @PostMapping("/information-requests/{rfiId}/respond")
    public ResponseEntity<ApiResponse<ApplicationInformationRequestEntity>> respondRfi(
            @PathVariable UUID rfiId, @RequestBody RespondRfiRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                governanceService.respondToInformationRequest(rfiId, request.responseNotes(),
                        request.responseDocumentId()), corr(ctx)));
    }

    @PostMapping("/information-requests/{rfiId}/close")
    public ResponseEntity<ApiResponse<ApplicationInformationRequestEntity>> closeRfi(
            @PathVariable UUID rfiId, @RequestParam(defaultValue = "true") boolean satisfied) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.closeInformationRequest(rfiId, satisfied), corr(ctx)));
    }

    @GetMapping("/applications/{applicationId}/information-requests")
    public ResponseEntity<ApiResponse<List<ApplicationInformationRequestEntity>>> listRfis(@PathVariable UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.listInformationRequests(applicationId), corr(ctx)));
    }

    // ---- External council review -------------------------------------------

    @PostMapping("/applications/{applicationId}/council-reviews")
    public ResponseEntity<ApiResponse<ExternalCouncilReviewEntity>> recordCouncilReview(
            @PathVariable UUID applicationId,
            @RequestBody ApplicationGovernanceService.RecordCouncilReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ApplicationGovernanceService.RecordCouncilReviewRequest bound =
                new ApplicationGovernanceService.RecordCouncilReviewRequest(applicationId,
                        request.reviewingAuthority(), request.referenceNumber(), request.submittedDate(),
                        request.decisionDate(), request.status(), request.conditions(), request.notes(),
                        request.evidenceDocumentId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(governanceService.recordCouncilReview(bound), corr(ctx)));
    }

    @GetMapping("/applications/{applicationId}/council-reviews")
    public ResponseEntity<ApiResponse<List<ExternalCouncilReviewEntity>>> listCouncilReviews(@PathVariable UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.listCouncilReviews(applicationId), corr(ctx)));
    }

    // ---- PIC nominations -----------------------------------------------------

    @PostMapping("/pic-nominations")
    public ResponseEntity<ApiResponse<PicNominationEntity>> nominate(
            @RequestBody PicNominationService.NominateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(picNominationService.nominate(request), corr(ctx)));
    }

    public record PicResponseRequest(String response, String notes) {}

    @PostMapping("/pic-nominations/{nominationId}/respond")
    public ResponseEntity<ApiResponse<PicNominationEntity>> respond(
            @PathVariable UUID nominationId, @RequestBody PicResponseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                picNominationService.respond(nominationId, request.response(), request.notes()), corr(ctx)));
    }

    public record PicReviewRequest(String authority, String reference, boolean approved, String notes) {}

    @PostMapping("/pic-nominations/{nominationId}/review")
    public ResponseEntity<ApiResponse<PicNominationEntity>> review(
            @PathVariable UUID nominationId, @RequestBody PicReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(picNominationService.recordReview(
                nominationId, request.authority(), request.reference(), request.approved(), request.notes()), corr(ctx)));
    }

    @PostMapping("/pic-nominations/{nominationId}/activate")
    public ResponseEntity<ApiResponse<PicNominationEntity>> activate(@PathVariable UUID nominationId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(picNominationService.activate(nominationId), corr(ctx)));
    }

    @PostMapping("/pic-nominations/{nominationId}/withdraw")
    public ResponseEntity<ApiResponse<PicNominationEntity>> withdraw(
            @PathVariable UUID nominationId, @RequestBody(required = false) Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(picNominationService.withdraw(
                nominationId, body != null ? body.get("reason") : null), corr(ctx)));
    }

    @GetMapping("/facilities/{facilityId}/pic-nominations")
    public ResponseEntity<ApiResponse<List<PicNominationEntity>>> facilityNominations(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(picNominationService.listForFacility(facilityId), corr(ctx)));
    }

    @GetMapping("/pic-nominations/by-provider/{providerPublicId}")
    public ResponseEntity<ApiResponse<List<PicNominationEntity>>> providerNominations(@PathVariable String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(picNominationService.listForPractitioner(providerPublicId), corr(ctx)));
    }

    public record ResolvePicReviewRequest(boolean cleared, String notes) {}

    @PostMapping("/pic-assignments/{assignmentId}/resolve-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolvePicReview(
            @PathVariable Long assignmentId, @RequestBody ResolvePicReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        var assignment = picNominationService.resolveReview(assignmentId, request.cleared(), request.notes());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "assignmentId", assignment.getId(),
                "reviewState", assignment.getReviewState()), corr(ctx)));
    }

    // ---- Inspection visits + structured responses ----------------------------

    @PostMapping("/inspections/{inspectionId}/visits")
    public ResponseEntity<ApiResponse<InspectionVisitEntity>> createVisit(
            @PathVariable UUID inspectionId, @RequestBody InspectionVisitService.CreateVisitRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        InspectionVisitService.CreateVisitRequest bound = new InspectionVisitService.CreateVisitRequest(
                inspectionId, request.scheduledDate(), request.mode(), request.leadInspectorId(),
                request.leadInspectorName(), request.team(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(visitService.createVisit(bound), corr(ctx)));
    }

    @GetMapping("/inspections/{inspectionId}/visits")
    public ResponseEntity<ApiResponse<List<InspectionVisitEntity>>> listVisits(@PathVariable UUID inspectionId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(visitService.listVisits(inspectionId), corr(ctx)));
    }

    public record ResponsesRequest(List<InspectionVisitService.ItemResponse> items) {}

    @PostMapping("/visits/{visitId}/responses")
    public ResponseEntity<ApiResponse<List<InspectionResponseEntity>>> recordResponses(
            @PathVariable UUID visitId, @RequestBody ResponsesRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                visitService.recordResponses(visitId, request.items()), corr(ctx)));
    }

    @GetMapping("/visits/{visitId}/responses")
    public ResponseEntity<ApiResponse<List<InspectionResponseEntity>>> listResponses(@PathVariable UUID visitId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(visitService.listResponses(visitId), corr(ctx)));
    }

    @PostMapping("/visits/{visitId}/complete")
    public ResponseEntity<ApiResponse<InspectionVisitEntity>> completeVisit(
            @PathVariable UUID visitId, @RequestBody(required = false) Map<String, String> body) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(visitService.completeVisit(
                visitId, body != null ? body.get("notes") : null), corr(ctx)));
    }

    // --- Manual-derived inspection content catalogue (V019) ---

    @GetMapping("/inspection-modules")
    public ResponseEntity<ApiResponse<List<InspectionRequirementModuleEntity>>> listInspectionModules() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(contentService.listModules(), corr(ctx)));
    }

    @GetMapping("/inspection-compositions")
    public ResponseEntity<ApiResponse<List<InspectionTemplateCompositionEntity>>> listInspectionCompositions() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(contentService.listCompositions(), corr(ctx)));
    }

    @GetMapping("/inspection-applicability")
    public ResponseEntity<ApiResponse<List<ClassificationTemplateApplicabilityEntity>>> listInspectionApplicability() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(contentService.listApplicability(), corr(ctx)));
    }

    @GetMapping("/inspections/{inspectionId}/checklist")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inspectionChecklist(@PathVariable UUID inspectionId) {
        TrustContext ctx = TrustContextHolder.require();
        var inspection = inspectionRepository.findById(inspectionId)
                .filter(i -> i.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Inspection not found: " + inspectionId));
        return ResponseEntity.ok(ApiResponse.ok(contentService.checklistFor(inspection), corr(ctx)));
    }

    @GetMapping("/visits/{visitId}/progress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> visitProgress(@PathVariable UUID visitId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(visitService.progress(visitId), corr(ctx)));
    }

    /**
     * Aggregate-only demand signal for marketplace supply-side insights.
     * Never exposes facility/application identifiers; small cells suppressed.
     */
    @GetMapping("/demand-signal")
    public ResponseEntity<ApiResponse<RegulatoryDemandSignalService.DemandSignalResponse>> demandSignal() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(demandSignalService.demandSignal(), corr(ctx)));
    }

    private static String corr(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : "";
    }
}
