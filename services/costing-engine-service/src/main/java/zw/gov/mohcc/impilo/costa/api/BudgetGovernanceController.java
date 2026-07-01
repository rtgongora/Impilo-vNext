package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.*;
import zw.gov.mohcc.impilo.costa.service.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Budget governance surface: revisions/reprogramming, recommendations,
 * thresholds and reconciliation exceptions. Shares the
 * {@code /internal/v1/finance/budgets} namespace.
 */
@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class BudgetGovernanceController {

    private final BudgetRevisionService revisionService;
    private final BudgetRecommendationService recommendationService;
    private final BudgetThresholdService thresholdService;
    private final BudgetReconciliationService reconciliationService;

    public BudgetGovernanceController(BudgetRevisionService revisionService,
                                      BudgetRecommendationService recommendationService,
                                      BudgetThresholdService thresholdService,
                                      BudgetReconciliationService reconciliationService) {
        this.revisionService = revisionService;
        this.recommendationService = recommendationService;
        this.thresholdService = thresholdService;
        this.reconciliationService = reconciliationService;
    }

    // ----- revisions --------------------------------------------------------

    @PostMapping("/managed/{budgetId}/revisions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyRevision(
            @PathVariable UUID budgetId, @Valid @RequestBody ApplyRevisionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var adjustments = body.adjustments().stream()
                .map(a -> new BudgetRevisionService.LineAdjustment(a.lineId(), a.newAmount()))
                .collect(Collectors.toList());
        var rev = revisionService.apply(ctx.tenantId(), budgetId, ctx.actorId(),
                new BudgetRevisionService.RevisionRequest(body.revisionType(), body.reason(), adjustments));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.revision(rev), cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/revisions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listRevisions(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = revisionService.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::revision).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    // ----- recommendations --------------------------------------------------

    @PostMapping("/managed/{budgetId}/recommendations/evaluate")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> evaluate(
            @PathVariable UUID budgetId,
            @RequestParam(name = "as_of", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = recommendationService.evaluate(ctx.tenantId(), budgetId, asOf).stream()
                .map(BudgetJsonMapper::recommendation).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/recommendations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listRecommendations(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = recommendationService.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::recommendation).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @PostMapping("/recommendations/{recommendationId}/ack")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ack(@PathVariable UUID recommendationId) {
        TrustContext ctx = TrustContextHolder.require();
        var r = recommendationService.setStatus(ctx.tenantId(), recommendationId, "ACKED");
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.recommendation(r), cid(ctx)));
    }

    @PostMapping("/recommendations/{recommendationId}/dismiss")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dismiss(@PathVariable UUID recommendationId) {
        TrustContext ctx = TrustContextHolder.require();
        var r = recommendationService.setStatus(ctx.tenantId(), recommendationId, "DISMISSED");
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.recommendation(r), cid(ctx)));
    }

    // ----- thresholds -------------------------------------------------------

    @PostMapping("/thresholds")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createThreshold(
            @Valid @RequestBody CreateThresholdRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var t = thresholdService.create(ctx.tenantId(), new BudgetThresholdService.NewThreshold(
                body.budgetId(), body.budgetLineId(), body.metric(), body.warnAt(),
                body.hardStopAt(), body.allowEmergencyOverride()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.threshold(t), cid(ctx)));
    }

    @GetMapping("/thresholds")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listThresholds(
            @RequestParam(name = "budget_id", required = false) UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = thresholdService.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::threshold).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @DeleteMapping("/thresholds/{thresholdId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivateThreshold(@PathVariable UUID thresholdId) {
        TrustContext ctx = TrustContextHolder.require();
        var t = thresholdService.deactivate(ctx.tenantId(), thresholdId);
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.threshold(t), cid(ctx)));
    }

    // ----- reconciliation exceptions ---------------------------------------

    @PostMapping("/reconciliation/exceptions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openException(
            @Valid @RequestBody OpenReconciliationRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var e = reconciliationService.open(ctx.tenantId(), new BudgetReconciliationService.NewException(
                body.budgetId(), body.budgetLineId(), body.exceptionType(), body.source(),
                body.sourceReference(), body.expected(), body.observed()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.reconciliation(e), cid(ctx)));
    }

    @GetMapping("/reconciliation/exceptions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listOpenExceptions() {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = reconciliationService.listOpen(ctx.tenantId()).stream()
                .map(BudgetJsonMapper::reconciliation).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @PostMapping("/reconciliation/exceptions/{exceptionId}/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveException(
            @PathVariable UUID exceptionId, @Valid @RequestBody ResolveReconciliationRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var e = reconciliationService.resolve(ctx.tenantId(), exceptionId, body.status(), body.note());
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.reconciliation(e), cid(ctx)));
    }

    private static String cid(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
