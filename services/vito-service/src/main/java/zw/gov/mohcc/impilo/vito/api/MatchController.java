package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.core.MatchDisposition;
import zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;

import java.util.List;
import java.util.UUID;

/**
 * Identity Resolution API — dual-mode.
 * Both modes can trigger matching. Resolution of pending matches is INTERNAL only.
 */
@RestController
@RequestMapping("/v1/match")
public class MatchController {

    private final MatchingEngine matchingEngine;

    public MatchController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    /**
     * Trigger matching for a source client — both modes.
     * OpenCR $match interop is handled transparently by the engine.
     */
    @PostMapping("/{healthId}")
    public ResponseEntity<ApiResponse<List<MatchResultEntity>>> findMatches(
            @PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        List<MatchResultEntity> results = matchingEngine.findMatches(ctx.tenantId(), healthId);
        return ResponseEntity.ok(ApiResponse.ok(results, ctx.correlationId().toString()));
    }

    /**
     * Get pending match queue for operator resolution.
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PagedResponse<MatchResultEntity>>> getPendingMatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<MatchResultEntity> results = matchingEngine.getPendingMatches(
                ctx.tenantId(), PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    /**
     * Resolve a pending match result — INTERNAL only.
     */
    @PostMapping("/{matchId}/resolve")
    public ResponseEntity<ApiResponse<MatchResultEntity>> resolveMatch(
            @PathVariable Long matchId,
            @RequestBody ResolveMatchRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        MatchResultEntity resolved = matchingEngine.resolve(
                matchId, request.disposition(), ctx.actorId());
        return ResponseEntity.ok(ApiResponse.ok(resolved, ctx.correlationId().toString()));
    }

    record ResolveMatchRequest(MatchDisposition disposition) {}
}
