package zw.gov.mohcc.impilo.gl.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.TrialBalanceService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/financial-statements")
public class GlFinancialStatementsController {

    private final TrialBalanceService trialBalanceService;

    public GlFinancialStatementsController(TrialBalanceService trialBalanceService) {
        this.trialBalanceService = trialBalanceService;
    }

    @GetMapping("/income-statement")
    public ResponseEntity<Map<String, Object>> income(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(trialBalanceService.incomeStatement(tenant, periodId));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<Map<String, Object>> balanceSheet(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(trialBalanceService.balanceSheet(tenant, periodId));
    }
}
