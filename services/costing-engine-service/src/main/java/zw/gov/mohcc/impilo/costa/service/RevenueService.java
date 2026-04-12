package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.RevenueEntryEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.RevenueEntryRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RevenueService {

    public static final String REVENUE_TYPE_BILL_FINAL = "BILL_FINAL";

    private final RevenueEntryRepository revenueEntryRepository;

    public RevenueService(RevenueEntryRepository revenueEntryRepository) {
        this.revenueEntryRepository = revenueEntryRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasRecordedForBill(UUID tenantId, String billId) {
        return revenueEntryRepository.existsByTenantIdAndBillId(tenantId, billId);
    }

    /**
     * Records consolidated encounter revenue when a bill is finalized (idempotent per bill).
     */
    @Transactional
    public RevenueEntryEntity recordFromFinalizedBill(BillHeaderEntity bill) {
        if (hasRecordedForBill(bill.getTenantId(), bill.getBillId())) {
            return revenueEntryRepository.findFirstByTenantIdAndBillIdOrderByCreatedAtDesc(
                            bill.getTenantId(), bill.getBillId())
                    .orElseThrow();
        }

        OffsetDateTime ref = bill.getFinalizedAt() != null ? bill.getFinalizedAt() : OffsetDateTime.now();
        int year = ref.getYear();
        int month = ref.getMonthValue();

        RevenueEntryEntity e = new RevenueEntryEntity();
        e.setTenantId(bill.getTenantId());
        e.setFacilityId(bill.getFacilityId());
        e.setEncounterId(bill.getEncounterId());
        e.setBillId(bill.getBillId());
        e.setRevenueType(REVENUE_TYPE_BILL_FINAL);
        e.setDescription("Finalized bill " + bill.getBillId());
        e.setGrossAmount(bill.getTotalCharge());
        e.setDiscountAmount(bill.getTotalDiscount() != null ? bill.getTotalDiscount() : BigDecimal.ZERO);
        e.setNetAmount(bill.getTotalPayable());
        e.setRecordedAt(ref);
        e.setPeriodYear(year);
        e.setPeriodMonth(month);
        return revenueEntryRepository.save(e);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> revenueSummary(UUID tenantId, UUID facilityId, int year, int month) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_id", tenantId.toString());
        m.put("facility_id", facilityId.toString());
        m.put("period_year", year);
        m.put("period_month", month);
        m.put("gross_total", revenueEntryRepository.sumGrossByFacilityAndPeriod(tenantId, facilityId, year, month));
        m.put("discount_total", revenueEntryRepository.sumDiscountByFacilityAndPeriod(tenantId, facilityId, year, month));
        m.put("net_total", revenueEntryRepository.sumNetByFacilityAndPeriod(tenantId, facilityId, year, month));
        return m;
    }

    @Transactional(readOnly = true)
    public List<RevenueEntryEntity> queryByFacilityDepartmentPeriod(UUID tenantId, UUID facilityId,
                                                                    String departmentId, int year, int month) {
        return revenueEntryRepository.findByFacilityPeriodAndOptionalDepartment(
                tenantId, facilityId, year, month, departmentId);
    }
}
