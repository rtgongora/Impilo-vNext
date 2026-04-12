package zw.gov.mohcc.impilo.costa.api.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntryEntity;
import zw.gov.mohcc.impilo.costa.service.CostCenterService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/finance/cost-centers")
public class CostCenterController {

    private final CostCenterService costCenterService;

    public CostCenterController(CostCenterService costCenterService) {
        this.costCenterService = costCenterService;
    }

    @GetMapping("/summary/budget-vs-actual")
    public ResponseEntity<List<Map<String, Object>>> budgetVsActual(
            @RequestParam("facility_id") UUID facilityId,
            @RequestParam("year") int year,
            @RequestParam(value = "month", required = false) Integer month) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(costCenterService.budgetVsActual(ctx.tenantId(), facilityId, year, month));
    }

    @GetMapping
    public ResponseEntity<List<CostCenterEntity>> list(@RequestParam("facility_id") UUID facilityId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(costCenterService.list(ctx.tenantId(), facilityId));
    }

    @GetMapping("/{centerId}")
    public ResponseEntity<CostCenterEntity> get(
            @RequestParam("facility_id") UUID facilityId,
            @PathVariable("centerId") UUID centerId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(costCenterService.get(ctx.tenantId(), facilityId, centerId));
    }

    @PostMapping
    public ResponseEntity<CostCenterEntity> create(@RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        CostCenterEntity c = new CostCenterEntity();
        c.setTenantId(ctx.tenantId());
        c.setFacilityId(UUID.fromString(body.get("facility_id").toString()));
        c.setName(body.get("name").toString());
        c.setCode(body.get("code").toString());
        if (body.get("parent_center_id") != null) {
            c.setParentCenterId(UUID.fromString(body.get("parent_center_id").toString()));
        }
        if (body.get("center_type") != null) {
            c.setCenterType(body.get("center_type").toString());
        }
        if (body.get("budget_amount") != null) {
            c.setBudgetAmount(new BigDecimal(body.get("budget_amount").toString()));
        }
        if (body.get("budget_period") != null) {
            c.setBudgetPeriod(body.get("budget_period").toString());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(costCenterService.create(c));
    }

    @PutMapping("/{centerId}")
    public ResponseEntity<CostCenterEntity> update(
            @RequestParam("facility_id") UUID facilityId,
            @PathVariable("centerId") UUID centerId,
            @RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String centerType = body.get("center_type") != null ? body.get("center_type").toString() : null;
        BigDecimal budget = body.get("budget_amount") != null ? new BigDecimal(body.get("budget_amount").toString()) : null;
        String budgetPeriod = body.get("budget_period") != null ? body.get("budget_period").toString() : null;
        UUID parent = body.get("parent_center_id") != null ? UUID.fromString(body.get("parent_center_id").toString()) : null;
        return ResponseEntity.ok(costCenterService.update(ctx.tenantId(), facilityId, centerId, name, centerType, budget, budgetPeriod, parent));
    }

    @DeleteMapping("/{centerId}")
    public ResponseEntity<Void> delete(
            @RequestParam("facility_id") UUID facilityId,
            @PathVariable("centerId") UUID centerId) {
        var ctx = TrustContextHolder.require();
        costCenterService.delete(ctx.tenantId(), facilityId, centerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{centerId}/expenses")
    public ResponseEntity<CostCenterEntryEntity> recordExpense(
            @RequestParam("facility_id") UUID facilityId,
            @PathVariable("centerId") UUID centerId,
            @RequestBody Map<String, Object> body) {
        var ctx = TrustContextHolder.require();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.get("description") != null ? body.get("description").toString() : null;
        String refType = body.get("reference_type") != null ? body.get("reference_type").toString() : null;
        String refId = body.get("reference_id") != null ? body.get("reference_id").toString() : null;
        int year = Integer.parseInt(body.get("period_year").toString());
        int month = Integer.parseInt(body.get("period_month").toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                costCenterService.recordExpense(ctx.tenantId(), facilityId, centerId, amount, description, refType, refId, year, month));
    }
}
