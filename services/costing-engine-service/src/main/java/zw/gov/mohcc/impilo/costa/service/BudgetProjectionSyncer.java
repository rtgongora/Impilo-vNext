package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetAllocationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetLineRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the flat {@code costa_budget_allocations} availability projection in
 * sync with an active budget version's lines. Facility-scoped only (the
 * projection is facility-grained); non-facility budgets are left to the rich
 * model. Committed/spent amounts on the projection are preserved.
 */
@Component
public class BudgetProjectionSyncer {

    private static final Logger log = LoggerFactory.getLogger(BudgetProjectionSyncer.class);

    private final BudgetAllocationRepository allocationRepository;
    private final BudgetLineRepository lineRepository;

    public BudgetProjectionSyncer(BudgetAllocationRepository allocationRepository,
                                  BudgetLineRepository lineRepository) {
        this.allocationRepository = allocationRepository;
        this.lineRepository = lineRepository;
    }

    public void sync(UUID tenantId, BudgetEntity budget, UUID versionId) {
        if (budget.getFacilityId() == null) {
            log.info("Budget {} has no facility scope; skipping allocation projection sync", budget.getBudgetId());
            return;
        }
        List<BudgetLineEntity> lines =
                lineRepository.findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(versionId, tenantId);

        Map<String, BigDecimal> byKey = new LinkedHashMap<>();
        Map<String, BudgetLineEntity> sample = new LinkedHashMap<>();
        for (BudgetLineEntity line : lines) {
            String key = (line.getDepartmentId() == null ? "" : line.getDepartmentId())
                    + "|" + line.getBudgetCategory();
            byKey.merge(key, line.getAllocatedAmount(), BigDecimal::add);
            sample.putIfAbsent(key, line);
        }
        for (Map.Entry<String, BigDecimal> e : byKey.entrySet()) {
            BudgetLineEntity s = sample.get(e.getKey());
            BudgetAllocationEntity alloc = allocationRepository.findForSpendLine(
                            tenantId, budget.getFacilityId(), s.getDepartmentId(),
                            s.getBudgetCategory(), budget.getPeriodYear())
                    .orElseGet(() -> {
                        BudgetAllocationEntity a = new BudgetAllocationEntity();
                        a.setTenantId(tenantId);
                        a.setFacilityId(budget.getFacilityId());
                        a.setDepartmentId(s.getDepartmentId());
                        a.setBudgetCategory(s.getBudgetCategory());
                        a.setPeriodYear(budget.getPeriodYear());
                        a.setSpentAmount(BigDecimal.ZERO);
                        a.setCommittedAmount(BigDecimal.ZERO);
                        return a;
                    });
            alloc.setAllocatedAmount(e.getValue());
            alloc.recomputeAvailable();
            allocationRepository.save(alloc);
            if (s.getAllocationId() == null && alloc.getAllocationId() != null) {
                s.setAllocationId(alloc.getAllocationId());
                lineRepository.save(s);
            }
        }
    }
}
