package zw.gov.mohcc.impilo.gl.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.gl.persistence.entity.FiscalPeriodEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.FiscalPeriodRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FiscalPeriodService {

    private final FiscalPeriodRepository fiscalPeriodRepository;

    public FiscalPeriodService(FiscalPeriodRepository fiscalPeriodRepository) {
        this.fiscalPeriodRepository = fiscalPeriodRepository;
    }

    public List<FiscalPeriodEntity> openPeriods(UUID tenantId) {
        return fiscalPeriodRepository.findOpenPeriods(tenantId);
    }

    @Transactional
    public FiscalPeriodEntity ensurePeriodForDate(UUID tenantId, LocalDate date) {
        return fiscalPeriodRepository.findContainingDate(tenantId, date).orElseGet(() -> {
            int year = date.getYear();
            int month = date.getMonthValue();
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            FiscalPeriodEntity p = new FiscalPeriodEntity();
            p.setTenantId(tenantId);
            p.setFiscalYear(year);
            p.setPeriodNumber(month);
            p.setStartDate(start);
            p.setEndDate(end);
            p.setStatus("OPEN");
            return fiscalPeriodRepository.save(p);
        });
    }

    @Transactional
    public FiscalPeriodEntity closePeriod(UUID tenantId, UUID periodId, String closedBy) {
        FiscalPeriodEntity p = fiscalPeriodRepository.findById(periodId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Period not found"));
        if (!"OPEN".equals(p.getStatus())) {
            throw new IllegalStateException("Period is not OPEN");
        }
        p.setStatus("CLOSED");
        p.setClosedBy(closedBy);
        p.setClosedAt(OffsetDateTime.now());
        return fiscalPeriodRepository.save(p);
    }

    /**
     * Marks all open periods for the fiscal year as closed. Carry-forward journal entries
     * should be posted separately via {@link JournalService} when net income is computed.
     */
    @Transactional
    public void yearEndClose(UUID tenantId, int fiscalYear, String actor) {
        List<FiscalPeriodEntity> periods = fiscalPeriodRepository.findByTenantIdAndFiscalYearOrderByPeriodNumberAsc(
                tenantId, fiscalYear);
        for (FiscalPeriodEntity p : periods) {
            if ("OPEN".equals(p.getStatus())) {
                p.setStatus("CLOSED");
                p.setClosedBy(actor);
                p.setClosedAt(OffsetDateTime.now());
                fiscalPeriodRepository.save(p);
            }
        }
    }
}
