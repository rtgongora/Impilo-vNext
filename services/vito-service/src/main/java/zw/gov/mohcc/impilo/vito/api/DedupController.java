package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine;
import zw.gov.mohcc.impilo.vito.core.merge.MergeService;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.DedupCaseRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deduplication management endpoints — INTERNAL only.
 *
 * Provides dedup case queue, scoring, and merge/unmerge operations.
 */
@RestController
@RequestMapping("/v1/internal/dedup")
public class DedupController {

    private final DedupCaseRepository dedupCaseRepository;
    private final MatchingEngine matchingEngine;
    private final MergeService mergeService;

    public DedupController(DedupCaseRepository dedupCaseRepository,
                           MatchingEngine matchingEngine,
                           MergeService mergeService) {
        this.dedupCaseRepository = dedupCaseRepository;
        this.matchingEngine = matchingEngine;
        this.mergeService = mergeService;
    }

    /**
     * POST /v1/internal/dedup/score — compute match score between two clients.
     * Used by operators to evaluate potential duplicates before merging.
     */
    @PostMapping("/score")
    public ResponseEntity<?> score(@RequestBody ScoreRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        UUID tenantId = ctx.tenantId();
        List<MatchResultEntity> results = matchingEngine.findMatches(tenantId, request.healthIdA());

        // Filter to the specific candidate
        var scoredMatch = results.stream()
                .filter(r -> r.getCandidateHealthId().equals(request.healthIdB()))
                .findFirst();

        if (scoredMatch.isPresent()) {
            MatchResultEntity match = scoredMatch.get();
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "healthIdA", request.healthIdA().toString(),
                    "healthIdB", request.healthIdB().toString(),
                    "score", match.getMatchScore(),
                    "algorithm", match.getMatchAlgorithm(),
                    "fieldScores", match.getMatchFields(),
                    "disposition", match.getDisposition().name()
            ), ctx.correlationId().toString()));
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "healthIdA", request.healthIdA().toString(),
                "healthIdB", request.healthIdB().toString(),
                "score", 0.0,
                "algorithm", "PROBABILISTIC",
                "fieldScores", "{}",
                "disposition", "NO_MATCH"
        ), ctx.correlationId().toString()));
    }

    /**
     * GET /v1/internal/dedup/cases — dedup case queue.
     */
    @GetMapping("/cases")
    public ResponseEntity<?> cases(@RequestParam(defaultValue = "PENDING") String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        Page<DedupCaseEntity> cases = dedupCaseRepository.findByTenantIdAndStatus(
                ctx.tenantId(), status, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(cases.getContent(), page, size, cases.getTotalElements()),
                ctx.correlationId().toString()));
    }

    /**
     * POST /v1/internal/dedup/cases/{caseId}/merge — execute merge.
     */
    @PostMapping("/cases/{caseId}/merge")
    public ResponseEntity<?> merge(@PathVariable Long caseId,
                                    @RequestBody MergeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        var history = mergeService.merge(
                ctx.tenantId(), request.survivorCrid(), request.mergedCrid(),
                caseId, request.strategy(), request.fieldDecisions(),
                ctx.actorId(), ctx.actorType());

        return ResponseEntity.ok(ApiResponse.ok(history, ctx.correlationId().toString()));
    }

    /**
     * POST /v1/internal/dedup/merge/{mergeId}/unmerge — reverse a merge.
     */
    @PostMapping("/merge/{mergeId}/unmerge")
    public ResponseEntity<?> unmerge(@PathVariable Long mergeId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        var history = mergeService.unmerge(ctx.tenantId(), mergeId, ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(history, ctx.correlationId().toString()));
    }

    record ScoreRequest(UUID healthIdA, UUID healthIdB) {}
    record MergeRequest(UUID survivorCrid, UUID mergedCrid, String strategy,
                        String fieldDecisions) {}
}
