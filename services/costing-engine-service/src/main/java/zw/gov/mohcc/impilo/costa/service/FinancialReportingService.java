package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialPeriodEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialSummaryEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetAllocationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.FinancialAggregateRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.FinancialPeriodRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.FinancialSummaryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinancialReportingService {

    private final FinancialAggregateRepository aggregates;
    private final FinancialPeriodRepository periodRepository;
    private final FinancialSummaryRepository summaryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final ObjectMapper objectMapper;

    public FinancialReportingService(FinancialAggregateRepository aggregates,
                                     FinancialPeriodRepository periodRepository,
                                     FinancialSummaryRepository summaryRepository,
                                     BudgetAllocationRepository budgetAllocationRepository,
                                     ObjectMapper objectMapper) {
        this.aggregates = aggregates;
        this.periodRepository = periodRepository;
        this.summaryRepository = summaryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> generateRevenueSummary(UUID tenantId, UUID facilityId, int periodYear, int periodMonth) {
        return saveRevenueSnapshot(tenantId, facilityId, periodYear, periodMonth, null, "REVENUE_MONTHLY");
    }

    @Transactional
    public Map<String, Object> closePeriod(UUID tenantId, String periodType, int year, int month, String closedBy) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        FinancialPeriodEntity period = periodRepository
                .findByTenantIdAndPeriodTypeAndPeriodYearAndPeriodMonth(tenantId, periodType, year, month)
                .orElseGet(FinancialPeriodEntity::new);
        period.setTenantId(tenantId);
        period.setPeriodType(periodType);
        period.setPeriodYear(year);
        period.setPeriodMonth(month);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setStatus("CLOSED");
        period.setClosedBy(closedBy);
        period.setClosedAt(OffsetDateTime.now());
        period = periodRepository.save(period);

        Map<String, Object> revenue = saveRevenueSnapshot(tenantId, null, year, month, period.getPeriodId(), "PERIOD_CLOSE");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodId", period.getPeriodId().toString());
        out.put("status", period.getStatus());
        out.put("revenueSummary", revenue);
        return out;
    }

    private Map<String, Object> saveRevenueSnapshot(UUID tenantId,
                                                    UUID facilityId,
                                                    int periodYear,
                                                    int periodMonth,
                                                    UUID periodId,
                                                    String summaryType) {
        BigDecimal total = aggregates.sumNetRevenueForMonth(tenantId, facilityId, periodYear, periodMonth);
        int encounters = aggregates.countDistinctEncountersForMonth(tenantId, facilityId, periodYear, periodMonth);
        List<Map<String, Object>> byPayer = aggregates.revenueByPayerType(tenantId, facilityId, periodYear, periodMonth);
        List<Map<String, Object>> byDept = aggregates.revenueByDepartment(tenantId, facilityId, periodYear, periodMonth);
        List<Map<String, Object>> trend = aggregates.monthlyRevenueTrend(tenantId, facilityId, periodYear);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("byPayerType", byPayer);
        metadata.put("byDepartment", byDept);
        metadata.put("monthlyTrend", trend);

        FinancialSummaryEntity row = new FinancialSummaryEntity();
        row.setTenantId(tenantId);
        row.setFacilityId(facilityId);
        row.setPeriodId(periodId);
        row.setSummaryType(summaryType);
        row.setTotalRevenue(total);
        row.setEncounterCount(encounters);
        row.setMetadataJson(writeJson(metadata));
        summaryRepository.save(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summaryId", row.getSummaryId().toString());
        out.put("totalRevenue", total);
        out.put("encounterCount", encounters);
        out.put("byPayerType", byPayer);
        out.put("byDepartment", byDept);
        out.put("monthlyTrend", trend);
        return out;
    }

    public Map<String, Object> generateClaimsExpenditure(UUID tenantId, String payerId, int year, int month) {
        List<Map<String, Object>> byStatus = aggregates.claimsByStatusForPayer(tenantId, payerId, year, month);
        BigDecimal exposure = aggregates.sumClaimsExposureForPayer(tenantId, payerId, year, month);
        BigDecimal premiums = aggregates.sumPremiumsForPayer(tenantId, payerId, year, month);
        BigDecimal lossRatio = null;
        if (premiums.compareTo(BigDecimal.ZERO) > 0) {
            lossRatio = exposure.divide(premiums, 4, RoundingMode.HALF_UP);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("claimsByStatus", byStatus);
        out.put("claimsExposure", exposure);
        out.put("premiumsCollected", premiums);
        out.put("lossRatio", lossRatio);
        return out;
    }

    public Map<String, Object> generateProviderPaymentSummary(UUID tenantId, UUID providerId, int year, int month) {
        BigDecimal totalPaid = aggregates.sumProviderPayments(tenantId, providerId, year, month);
        List<Map<String, Object>> rows = aggregates.providerPaymentRows(tenantId, providerId, year, month);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("providerId", providerId.toString());
        out.put("totalPaid", totalPaid);
        out.put("byPaymentType", rows);
        return out;
    }

    public Map<String, Object> generateDebtAgingReport(UUID tenantId, UUID facilityId) {
        List<Map<String, Object>> buckets = aggregates.receivablesAgingBuckets(tenantId, facilityId);
        List<Map<String, Object>> debtors = aggregates.topDebtors(tenantId, facilityId, 20);
        BigDecimal writeOffs = aggregates.sumWriteOffs(tenantId, facilityId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agingBuckets", buckets);
        out.put("topDebtors", debtors);
        out.put("writeOffTotal", writeOffs);
        return out;
    }

    public Map<String, Object> generateBudgetVsActual(UUID tenantId, UUID facilityId, int periodYear) {
        List<BudgetAllocationEntity> allocations = budgetAllocationRepository
                .findByTenantIdAndFacilityIdAndPeriodYearOrderByBudgetCategory(tenantId, facilityId, periodYear);
        List<Map<String, Object>> actuals = aggregates.actualSpendByCostCenterCode(tenantId, facilityId, periodYear);

        Map<String, BigDecimal> actualByCode = new LinkedHashMap<>();
        for (Map<String, Object> a : actuals) {
            actualByCode.put(String.valueOf(a.get("categoryCode")), (BigDecimal) a.get("actualAmount"));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BudgetAllocationEntity b : allocations) {
            BigDecimal actual = actualByCode.getOrDefault(b.getBudgetCategory(), BigDecimal.ZERO);
            BigDecimal allocated = b.getAllocatedAmount();
            BigDecimal spent = b.getSpentAmount() != null ? b.getSpentAmount() : BigDecimal.ZERO;
            BigDecimal pct = BigDecimal.ZERO;
            if (allocated.compareTo(BigDecimal.ZERO) > 0) {
                pct = spent.multiply(BigDecimal.valueOf(100)).divide(allocated, 1, RoundingMode.HALF_UP);
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("allocationId", b.getAllocationId().toString());
            line.put("id", b.getId());
            line.put("category", b.getBudgetCategory());
            line.put("departmentId", b.getDepartmentId());
            line.put("allocated", allocated);
            line.put("spent", spent);
            line.put("committed", b.getCommittedAmount());
            line.put("available", b.getAvailableAmount());
            line.put("actualFromCostCenters", actual);
            line.put("percentUsed", pct);
            rows.add(line);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", periodYear);
        out.put("facilityId", facilityId.toString());
        out.put("rows", rows);
        out.put("costCenterActuals", actuals);
        return out;
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
