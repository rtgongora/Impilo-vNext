package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetForecastSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVersionEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetActualReferenceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetForecastSnapshotRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetVersionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetForecastServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetVersionRepository versionRepository;
    @Mock private BudgetActualReferenceRepository actualRepository;
    @Mock private BudgetForecastSnapshotRepository snapshotRepository;

    private BudgetForecastService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetForecastService(budgetRepository, versionRepository,
                actualRepository, snapshotRepository, new ObjectMapper());
        BudgetEntity b = new BudgetEntity();
        b.setBudgetId(budgetId);
        b.setTenantId(tenantId);
        b.setPeriodYear(2026);
        b.setPeriodStart(LocalDate.of(2026, 1, 1));
        b.setPeriodEnd(LocalDate.of(2026, 1, 10)); // 10-day period for deterministic math
        when(budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)).thenReturn(Optional.of(b));
        BudgetVersionEntity v = new BudgetVersionEntity();
        v.setTotalAmount(new BigDecimal("1200"));
        when(versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId)).thenReturn(Optional.of(v));
        when(actualRepository.sumActualByBudget(tenantId, budgetId)).thenReturn(new BigDecimal("300"));
    }

    @Test
    void linearYtd_projectsFromElapsedFraction() {
        // day 5 of 10 -> elapsed 0.5; actual 300 -> projected 600; overrun 600-1200=-600
        BudgetForecastSnapshotEntity f = service.compute(tenantId, budgetId, "LINEAR_YTD", LocalDate.of(2026, 1, 5));
        assertEquals("LINEAR_YTD", f.getMethod());
        assertEquals(0, new BigDecimal("0.500000").compareTo(f.getElapsedFraction()));
        assertEquals(0, new BigDecimal("600.00").compareTo(f.getProjectedYearEnd()));
        assertEquals(0, new BigDecimal("-600.00").compareTo(f.getProjectedOverrun()));
        assertTrue(f.getInputsJson().contains("\"daysElapsed\":5"));
    }

    @Test
    void burnRate_projectsFromDailyBurn() {
        // burn = 300/5 = 60/day; remaining 5 days -> projected 300 + 300 = 600
        BudgetForecastSnapshotEntity f = service.compute(tenantId, budgetId, "BURN_RATE", LocalDate.of(2026, 1, 5));
        assertEquals("BURN_RATE", f.getMethod());
        assertEquals(0, new BigDecimal("60.0000").compareTo(f.getBurnRate()));
        assertEquals(0, new BigDecimal("600.00").compareTo(f.getProjectedYearEnd()));
    }

    @Test
    void seasonalNaive_fallsBackToBurnRate_noFakeCurve() {
        BudgetForecastSnapshotEntity f = service.compute(tenantId, budgetId, "SEASONAL_NAIVE", LocalDate.of(2026, 1, 5));
        assertEquals("BURN_RATE", f.getMethod());
    }
}
