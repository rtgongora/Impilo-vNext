package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetAllocationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    private final BudgetAllocationRepository repository;

    public BudgetService(BudgetAllocationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BudgetAllocationEntity createAllocation(UUID tenantId,
                                                   UUID facilityId,
                                                   String departmentId,
                                                   String budgetCategory,
                                                   int year,
                                                   BigDecimal amount) {
        BudgetAllocationEntity e = new BudgetAllocationEntity();
        e.setTenantId(tenantId);
        e.setFacilityId(facilityId);
        e.setDepartmentId(departmentId);
        e.setBudgetCategory(budgetCategory);
        e.setPeriodYear(year);
        e.setAllocatedAmount(amount);
        e.setSpentAmount(BigDecimal.ZERO);
        e.setCommittedAmount(BigDecimal.ZERO);
        e.recomputeAvailable();
        return repository.save(e);
    }

    @Transactional
    public BudgetAllocationEntity recordSpend(UUID tenantId, long allocationId, BigDecimal amount) {
        BudgetAllocationEntity e = repository.findByIdAndTenantId(allocationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget allocation not found"));
        BigDecimal add = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal spent = e.getSpentAmount() != null ? e.getSpentAmount() : BigDecimal.ZERO;
        e.setSpentAmount(spent.add(add));
        e.recomputeAvailable();
        return repository.save(e);
    }

    public List<BudgetAllocationEntity> getBudgetStatus(UUID tenantId, UUID facilityId, int year) {
        return repository.findByTenantIdAndFacilityIdAndPeriodYearOrderByBudgetCategory(tenantId, facilityId, year);
    }
}
