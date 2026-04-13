package zw.gov.mohcc.impilo.gl.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.BudgetService;
import zw.gov.mohcc.impilo.gl.persistence.entity.BudgetLineEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/budgets")
public class GlBudgetsController {

    private final BudgetService budgetService;

    public GlBudgetsController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetLineEntity>> list(@RequestParam int fiscalYear) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(budgetService.list(tenant, fiscalYear));
    }

    @PostMapping
    public ResponseEntity<BudgetLineEntity> upsert(@RequestBody Map<String, Object> body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        UUID accountId = UUID.fromString(body.get("accountId").toString());
        int fy = Integer.parseInt(body.get("fiscalYear").toString());
        BigDecimal amt = new BigDecimal(body.get("budgetAmount").toString());
        return ResponseEntity.ok(budgetService.upsert(tenant, accountId, fy, amt));
    }

    @PostMapping("/refresh-actuals")
    public ResponseEntity<Void> refresh(@RequestParam int fiscalYear, @RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        budgetService.refreshActuals(tenant, fiscalYear, periodId);
        return ResponseEntity.accepted().build();
    }
}
