package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClassificationDtos.*;
import zw.gov.mohcc.impilo.tuso.core.FacilityClassificationEnrichmentService;
import zw.gov.mohcc.impilo.tuso.core.FacilityClassificationReviewService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEnrichmentRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitCandidateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityEnrichmentRunRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Facility classification reconciliation surface (TUSO SoR). Enrichment runs,
 * the reviewable profile queue + summary, per-facility fact-supply / confirm /
 * correct, deterministic-only bulk-confirm, and regulated-unit candidates.
 *
 * <p>Reads are tenant-scoped by {@code ctx.tenantId()}; mutations are additionally
 * restricted to registry-admin actor types in the service layer (403 otherwise).</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/classification")
public class FacilityClassificationController {

    private static final Logger log = LoggerFactory.getLogger(FacilityClassificationController.class);

    private final FacilityClassificationEnrichmentService enrichmentService;
    private final FacilityClassificationReviewService reviewService;
    private final FacilityEnrichmentRunRepository runRepository;

    public FacilityClassificationController(FacilityClassificationEnrichmentService enrichmentService,
                                            FacilityClassificationReviewService reviewService,
                                            FacilityEnrichmentRunRepository runRepository) {
        this.enrichmentService = enrichmentService;
        this.reviewService = reviewService;
        this.runRepository = runRepository;
    }

    // ── enrichment runs ──────────────────────────────────────────────────────

    @PostMapping("/enrichment-runs")
    public ResponseEntity<ApiResponse<RunView>> runEnrichment(@RequestBody(required = false) RunRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        boolean dryRun = req == null || req.dryRun() == null || req.dryRun();
        log.info("Classification enrichment run requested [dryRun={}] correlationId={}", dryRun, ctx.correlationId());
        RunView out = call(() -> toRunView(enrichmentService.runBackfill(ctx, dryRun)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, corr(ctx)));
    }

    @GetMapping("/enrichment-runs")
    public ResponseEntity<ApiResponse<List<RunView>>> listRuns() {
        TrustContext ctx = TrustContextHolder.require();
        List<RunView> out = runRepository.findTop50ByOrderByStartedAtDesc().stream().map(this::toRunView).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @GetMapping("/enrichment-runs/{runId}")
    public ResponseEntity<ApiResponse<RunView>> getRun(@PathVariable Long runId) {
        TrustContext ctx = TrustContextHolder.require();
        RunView out = call(() -> toRunView(runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId))));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    // ── profiles ─────────────────────────────────────────────────────────────

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<PagedResponse<ProfileView>>> listProfiles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String hpaClass,
            @RequestParam(required = false) String canonicalType,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<FacilityRegulatoryProfileEntity> result =
                reviewService.search(status, hpaClass, canonicalType, sourceType, page, size);
        PagedResponse<ProfileView> body = PagedResponse.of(
                result.getContent().stream().map(this::toProfileView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(body, corr(ctx)));
    }

    @GetMapping("/profiles/summary")
    public ResponseEntity<ApiResponse<SummaryView>> summary() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(reviewService.summary(), corr(ctx)));
    }

    @GetMapping("/facilities/{facilityId}")
    public ResponseEntity<ApiResponse<ProfileDetailView>> getProfile(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        ProfileDetailView out = call(() -> {
            ProfileView profile = toProfileView(reviewService.requireProfile(facilityId));
            List<HistoryView> history = reviewService.history(facilityId).stream().map(this::toHistoryView).toList();
            return new ProfileDetailView(profile, history);
        });
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/facilities/{facilityId}/facts")
    public ResponseEntity<ApiResponse<ProfileView>> supplyFacts(@PathVariable Long facilityId,
                                                                @RequestBody FactsRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ProfileView out = call(() -> toProfileView(reviewService.supplyFacts(ctx, facilityId, req)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/facilities/{facilityId}/confirm")
    public ResponseEntity<ApiResponse<ProfileView>> confirm(@PathVariable Long facilityId,
                                                            @RequestBody(required = false) ConfirmRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ProfileView out = call(() -> toProfileView(reviewService.confirm(ctx, facilityId, req)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/facilities/{facilityId}/correct")
    public ResponseEntity<ApiResponse<ProfileView>> correct(@PathVariable Long facilityId,
                                                            @RequestBody CorrectRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ProfileView out = call(() -> toProfileView(reviewService.correct(ctx, facilityId, req)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/bulk-confirm")
    public ResponseEntity<ApiResponse<BulkConfirmResult>> bulkConfirm(@RequestBody BulkConfirmRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        BulkConfirmResult out = call(() -> reviewService.bulkConfirm(ctx, req));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    // ── unit candidates ──────────────────────────────────────────────────────

    @GetMapping("/facilities/{facilityId}/unit-candidates")
    public ResponseEntity<ApiResponse<List<UnitCandidateView>>> listCandidates(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        List<UnitCandidateView> out = reviewService.listCandidates(facilityId).stream()
                .map(this::toCandidateView).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/facilities/{facilityId}/unit-candidates")
    public ResponseEntity<ApiResponse<UnitCandidateView>> proposeCandidate(@PathVariable Long facilityId,
                                                                           @RequestBody UnitCandidateRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        UnitCandidateView out = call(() -> toCandidateView(reviewService.proposeCandidate(ctx, facilityId, req)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/unit-candidates/{candidateId}/confirm")
    public ResponseEntity<ApiResponse<UnitCandidateView>> confirmCandidate(
            @PathVariable UUID candidateId, @RequestBody(required = false) UnitCandidateConfirmRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        UnitCandidateView out = call(() -> toCandidateView(reviewService.confirmCandidate(ctx, candidateId, req)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/unit-candidates/{candidateId}/reject")
    public ResponseEntity<ApiResponse<UnitCandidateView>> rejectCandidate(@PathVariable UUID candidateId) {
        TrustContext ctx = TrustContextHolder.require();
        UnitCandidateView out = call(() -> toCandidateView(reviewService.rejectCandidate(ctx, candidateId)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/facilities/{facilityId}/unit-candidates/complete-review")
    public ResponseEntity<ApiResponse<ProfileView>> completeUnitReview(@PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        ProfileView out = call(() -> toProfileView(reviewService.completeUnitReview(ctx, facilityId)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    // ── mappers + helpers ────────────────────────────────────────────────────

    private static String corr(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }

    /** Translate service IllegalArgument/Security exceptions into HTTP 404/403/400. */
    private static <T> T call(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Not-found and validation both surface as IllegalArgumentException here;
            // "not found" messages map to 404, the rest to 400.
            String msg = e.getMessage() == null ? "" : e.getMessage();
            HttpStatus status = msg.toLowerCase().contains("not found") || msg.startsWith("No classification profile")
                    || msg.startsWith("Run not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, msg);
        }
    }

    private RunView toRunView(FacilityEnrichmentRunEntity r) {
        return new RunView(r.getId(), r.isDryRun(), r.getRecordsTotal(), r.getRecordsCreated(), r.getRecordsUpdated(),
                r.getRecordsUnchanged(), r.getRecordsPreservedHuman(), r.getRecordsFailed(),
                r.getCountsByStatus(), r.getEstateAudit(), r.getExceptionReport(), r.getStatus(),
                r.getMappingRuleCode(), r.getMappingRuleVersion(), r.getStartedAt(), r.getCompletedAt(),
                r.getInitiatedBy());
    }

    private ProfileView toProfileView(FacilityRegulatoryProfileEntity p) {
        return new ProfileView(p.getProfileId(), p.getFacilityId(),
                p.getSourceFacilityType(), p.getSourceOwnership(), p.getSourceLevel(), p.getSourceBedCapacity(),
                p.getCanonicalType(), p.getOwnershipAuthority(), p.getHpaRoute(),
                p.getCareSetting(), p.getMobility(), p.getSpecialistScope(),
                p.getBedCountClaimed(), p.getBedCountConfirmed(), p.getBedBand(), p.getBedBandBasis(),
                p.getHpaClass(), p.getHpaClassJustification(), p.getHpaClassificationId(), p.getHpaClassificationLabel(),
                p.getClassificationStatus(), p.getDimensionStatuses(), p.getSuggestion(), p.getRequiredFacts(),
                p.getSourceFieldsUsed(), p.getMappingRuleCode(), p.getMappingRuleVersion(),
                p.getUnitsReviewStatus(), p.getConfirmedBy(), p.getConfirmedAt(), p.isReclassificationReviewRequired());
    }

    private HistoryView toHistoryView(FacilityRegulatoryProfileHistoryEntity h) {
        return new HistoryView(h.getHistoryId(), h.getChangeKind(), h.getPrevSnapshot(), h.getNewSnapshot(),
                h.getActor(), h.getRunId(), h.getNote(), h.getCreatedAt());
    }

    private UnitCandidateView toCandidateView(FacilityUnitCandidateEntity c) {
        return new UnitCandidateView(c.getCandidateId(), c.getFacilityId(), c.getSuggestedUnitType(), c.getOrigin(),
                c.getServicePresence(), c.getStatus(), c.getRationale(), c.getEvidenceRef(), c.getCreatedUnitId(),
                c.getProposedBy(), c.getDecidedBy(), c.getDecidedAt(), c.getCreatedAt());
    }
}
