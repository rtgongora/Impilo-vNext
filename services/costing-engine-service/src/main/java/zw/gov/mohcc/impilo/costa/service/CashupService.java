package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.DailyCashupEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.DailyCashupRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CashupService {

    private final DailyCashupRepository dailyCashupRepository;

    public CashupService(DailyCashupRepository dailyCashupRepository) {
        this.dailyCashupRepository = dailyCashupRepository;
    }

    @Transactional
    public DailyCashupEntity openDailyCashup(UUID tenantId, UUID facilityId, String cashierId,
                                             LocalDate cashupDate, String shiftId) {
        return dailyCashupRepository
                .findByTenantIdAndFacilityIdAndCashupDateAndCashierIdAndStatus(
                        tenantId, facilityId, cashupDate, cashierId, "OPEN")
                .orElseGet(() -> {
                    DailyCashupEntity c = new DailyCashupEntity();
                    c.setTenantId(tenantId);
                    c.setFacilityId(facilityId);
                    c.setCashierId(cashierId);
                    c.setShiftId(shiftId);
                    c.setCashupDate(cashupDate);
                    c.setStatus("OPEN");
                    recalculateTotals(c);
                    return dailyCashupRepository.save(c);
                });
    }

    @Transactional
    public DailyCashupEntity recordCollection(UUID cashupId, BigDecimal cash, BigDecimal card,
                                              BigDecimal mobile, BigDecimal insuranceBilled) {
        DailyCashupEntity c = dailyCashupRepository.findByCashupId(cashupId)
                .orElseThrow(() -> new IllegalArgumentException("Cashup not found"));
        if (!"OPEN".equals(c.getStatus())) {
            throw new IllegalStateException("Cashup is not open");
        }
        if (cash != null) {
            c.setCashCollected(nz(c.getCashCollected()).add(cash));
        }
        if (card != null) {
            c.setCardCollected(nz(c.getCardCollected()).add(card));
        }
        if (mobile != null) {
            c.setMobileCollected(nz(c.getMobileCollected()).add(mobile));
        }
        if (insuranceBilled != null) {
            c.setInsuranceBilled(nz(c.getInsuranceBilled()).add(insuranceBilled));
        }
        recalculateTotals(c);
        return dailyCashupRepository.save(c);
    }

    @Transactional
    public DailyCashupEntity closeCashup(UUID cashupId, String supervisorId, BigDecimal totalBilled, String notes) {
        DailyCashupEntity c = dailyCashupRepository.findByCashupId(cashupId)
                .orElseThrow(() -> new IllegalArgumentException("Cashup not found"));
        if (!"OPEN".equals(c.getStatus())) {
            throw new IllegalStateException("Cashup is not open");
        }
        c.setSupervisorId(supervisorId);
        if (totalBilled != null) {
            c.setTotalBilled(totalBilled);
        }
        c.setNotes(notes);
        recalculateTotals(c);
        c.setStatus("CLOSED");
        c.setClosedAt(OffsetDateTime.now());
        return dailyCashupRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<DailyCashupEntity> findByFacilityAndDate(UUID tenantId, UUID facilityId, LocalDate date) {
        return dailyCashupRepository.findByTenantIdAndFacilityIdAndCashupDateOrderByCreatedAtDesc(
                tenantId, facilityId, date);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static void recalculateTotals(DailyCashupEntity c) {
        BigDecimal total = nz(c.getCashCollected())
                .add(nz(c.getCardCollected()))
                .add(nz(c.getMobileCollected()))
                .add(nz(c.getInsuranceBilled()));
        c.setTotalCollected(total);
        c.setVariance(total.subtract(nz(c.getTotalBilled())));
    }
}
