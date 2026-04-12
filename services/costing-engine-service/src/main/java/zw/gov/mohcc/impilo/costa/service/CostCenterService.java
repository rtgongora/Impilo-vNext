package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.CostCenterEntryEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.CostCenterEntryRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.CostCenterRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CostCenterService {

    public static final String ENTRY_EXPENSE = "EXPENSE";

    private final CostCenterRepository costCenterRepository;
    private final CostCenterEntryRepository costCenterEntryRepository;

    public CostCenterService(CostCenterRepository costCenterRepository,
                             CostCenterEntryRepository costCenterEntryRepository) {
        this.costCenterRepository = costCenterRepository;
        this.costCenterEntryRepository = costCenterEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<CostCenterEntity> list(UUID tenantId, UUID facilityId) {
        return costCenterRepository.findByTenantIdAndFacilityIdOrderByCodeAsc(tenantId, facilityId);
    }

    @Transactional(readOnly = true)
    public CostCenterEntity get(UUID tenantId, UUID facilityId, UUID centerId) {
        return costCenterRepository.findByTenantIdAndFacilityIdAndCenterId(tenantId, facilityId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Cost center not found"));
    }

    @Transactional
    public CostCenterEntity create(CostCenterEntity center) {
        costCenterRepository.findByTenantIdAndFacilityIdAndCode(
                        center.getTenantId(), center.getFacilityId(), center.getCode())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Cost center code already exists for facility");
                });
        return costCenterRepository.save(center);
    }

    @Transactional
    public CostCenterEntity update(UUID tenantId, UUID facilityId, UUID centerId, String name,
                                   String centerType, BigDecimal budgetAmount, String budgetPeriod,
                                   UUID parentCenterId) {
        CostCenterEntity c = get(tenantId, facilityId, centerId);
        if (name != null) {
            c.setName(name);
        }
        if (centerType != null) {
            c.setCenterType(centerType);
        }
        if (budgetAmount != null) {
            c.setBudgetAmount(budgetAmount);
        }
        if (budgetPeriod != null) {
            c.setBudgetPeriod(budgetPeriod);
        }
        if (parentCenterId != null) {
            c.setParentCenterId(parentCenterId);
        }
        return costCenterRepository.save(c);
    }

    @Transactional
    public void delete(UUID tenantId, UUID facilityId, UUID centerId) {
        CostCenterEntity c = get(tenantId, facilityId, centerId);
        if (costCenterEntryRepository.existsByCenterId(c.getCenterId())) {
            throw new IllegalStateException("Cannot delete cost center with ledger entries");
        }
        costCenterRepository.delete(c);
    }

    @Transactional
    public CostCenterEntryEntity recordExpense(UUID tenantId, UUID facilityId, UUID centerId,
                                               BigDecimal amount, String description,
                                               String referenceType, String referenceId,
                                               int periodYear, int periodMonth) {
        get(tenantId, facilityId, centerId);
        CostCenterEntryEntity e = new CostCenterEntryEntity();
        e.setTenantId(tenantId);
        e.setCenterId(centerId);
        e.setEntryType(ENTRY_EXPENSE);
        e.setAmount(amount);
        e.setDescription(description);
        e.setReferenceType(referenceType);
        e.setReferenceId(referenceId);
        e.setPeriodYear(periodYear);
        e.setPeriodMonth(periodMonth);
        return costCenterEntryRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> budgetVsActual(UUID tenantId, UUID facilityId, int year, Integer month) {
        List<CostCenterEntity> centers = list(tenantId, facilityId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CostCenterEntity c : centers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("center_id", c.getCenterId().toString());
            row.put("code", c.getCode());
            row.put("name", c.getName());
            BigDecimal budget = c.getBudgetAmount() != null ? c.getBudgetAmount() : BigDecimal.ZERO;
            row.put("budget_amount", budget);
            row.put("budget_period", c.getBudgetPeriod());

            BigDecimal actual;
            BigDecimal proratedBudget;
            if (month != null) {
                actual = costCenterEntryRepository.sumAmountByCenterPeriodAndType(
                        c.getCenterId(), year, month, ENTRY_EXPENSE);
                proratedBudget = "ANNUAL".equalsIgnoreCase(c.getBudgetPeriod())
                        ? budget.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                        : budget;
            } else {
                actual = costCenterEntryRepository.sumAmountByCenterYearAndType(
                        c.getCenterId(), year, ENTRY_EXPENSE);
                proratedBudget = budget;
            }
            row.put("actual_expense", actual);
            row.put("budget_compare_basis", proratedBudget);
            row.put("variance", proratedBudget.subtract(actual));
            rows.add(row);
        }
        return rows;
    }
}
