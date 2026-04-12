package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.BudgetAllocationCreateRequest;
import zw.gov.mohcc.impilo.costa.api.dto.financial.BudgetSpendRequest;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
import zw.gov.mohcc.impilo.costa.service.BudgetService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody BudgetAllocationCreateRequest body) {
        var ctx = TrustContextHolder.require();
        BudgetAllocationEntity e = budgetService.createAllocation(
                ctx.tenantId(),
                body.facilityId(),
                body.departmentId(),
                body.budgetCategory(),
                body.periodYear(),
                body.allocatedAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(e), ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam int year) {
        var ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = budgetService.getBudgetStatus(ctx.tenantId(), facilityId, year).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @PutMapping("/{id}/spend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordSpend(
            @PathVariable long id,
            @Valid @RequestBody BudgetSpendRequest body) {
        var ctx = TrustContextHolder.require();
        BudgetAllocationEntity e = budgetService.recordSpend(ctx.tenantId(), id, body.amount());
        return ResponseEntity.ok(ApiResponse.ok(toMap(e), ctx.correlationId().toString()));
    }

    private Map<String, Object> toMap(BudgetAllocationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("allocationId", e.getAllocationId().toString());
        m.put("facilityId", e.getFacilityId().toString());
        m.put("departmentId", e.getDepartmentId());
        m.put("budgetCategory", e.getBudgetCategory());
        m.put("periodYear", e.getPeriodYear());
        m.put("allocatedAmount", e.getAllocatedAmount());
        m.put("spentAmount", e.getSpentAmount());
        m.put("committedAmount", e.getCommittedAmount());
        m.put("availableAmount", e.getAvailableAmount());
        m.put("status", e.getStatus());
        return m;
    }
}
