package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.AddBudgetLineRequest;
import zw.gov.mohcc.impilo.costa.api.dto.financial.BudgetTransitionRequest;
import zw.gov.mohcc.impilo.costa.api.dto.financial.CreateBudgetRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.service.BudgetLifecycleService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Managed (rich, versioned, governed) budget lifecycle. Lives alongside the
 * legacy flat allocation endpoints on {@code BudgetController} — those are
 * unchanged. Managed budgets are namespaced under {@code /managed}.
 */
@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class BudgetLifecycleController {

    private final BudgetLifecycleService service;

    public BudgetLifecycleController(BudgetLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/managed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateBudgetRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.createBudget(ctx.tenantId(), ctx.actorId(),
                new BudgetLifecycleService.NewBudget(
                        body.facilityId(), body.scopeLevel(), body.periodYear(),
                        body.periodStart(), body.periodEnd(), body.title(),
                        body.grantId(), body.programmeCode(), body.fundingSourceId(),
                        body.currency(), body.notes()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @GetMapping("/managed")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "facility_id", required = false) UUID facilityId,
            @RequestParam(name = "year", required = false) Integer year) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.listBudgets(ctx.tenantId(), facilityId, year).stream()
                .map(BudgetJsonMapper::budget).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.requireBudget(ctx.tenantId(), budgetId);
        Map<String, Object> view = BudgetJsonMapper.budget(b);
        view.put("lines", service.currentLines(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::line).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.ok(view, cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/lines")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addLine(
            @PathVariable UUID budgetId, @Valid @RequestBody AddBudgetLineRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetLineEntity l = service.addLine(ctx.tenantId(), budgetId,
                new BudgetLifecycleService.NewLine(
                        body.costCenterId(), body.departmentId(), body.budgetCategory(),
                        body.fundingSourceId(), body.programmeCode(), body.grantId(),
                        body.districtCode(), body.glAccountRef(), body.allocatedAmount(),
                        body.lineOrder()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.line(l), cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/lines")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> lines(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.currentLines(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::line).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> versions(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.versions(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::version).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/approvals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> approvals(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.approvals(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::approval).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    // ----- lifecycle transitions -------------------------------------------

    @PostMapping("/managed/{budgetId}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.submit(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> review(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.review(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.approve(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.reject(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/return")
    public ResponseEntity<ApiResponse<Map<String, Object>>> returnToDraft(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.returnToDraft(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.activate(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/freeze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> freeze(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.freeze(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(
            @PathVariable UUID budgetId, @RequestBody(required = false) BudgetTransitionRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetEntity b = service.close(ctx.tenantId(), budgetId, ctx.actorId(), reason(body));
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.budget(b), cid(ctx)));
    }

    private static String reason(BudgetTransitionRequest body) {
        return body == null ? null : body.reason();
    }

    private static String cid(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
