package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.CloseBudgetPeriodRequest;
import zw.gov.mohcc.impilo.costa.api.dto.financial.ReopenPeriodRequest;
import zw.gov.mohcc.impilo.costa.service.BudgetPeriodCloseService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Budget period close / reopen / carry-forward. */
@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class BudgetPeriodCloseController {

    private final BudgetPeriodCloseService service;

    public BudgetPeriodCloseController(BudgetPeriodCloseService service) {
        this.service = service;
    }

    @PostMapping("/managed/{budgetId}/period-close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> close(
            @PathVariable UUID budgetId, @Valid @RequestBody CloseBudgetPeriodRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var e = service.close(ctx.tenantId(), budgetId, ctx.actorId(),
                new BudgetPeriodCloseService.CloseRequest(body.financialPeriodId(), body.status(),
                        body.carryForwardAmount(), body.notes()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.periodClose(e), cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/period-close")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = service.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::periodClose).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @PostMapping("/period-close/{closeId}/reopen")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reopen(
            @PathVariable UUID closeId, @RequestBody(required = false) ReopenPeriodRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var e = service.reopen(ctx.tenantId(), closeId, ctx.actorId(), body == null ? null : body.note());
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.periodClose(e), cid(ctx)));
    }

    private static String cid(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
