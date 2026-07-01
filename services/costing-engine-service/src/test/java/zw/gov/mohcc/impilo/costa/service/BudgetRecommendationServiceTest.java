package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetForecastSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetRecommendationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVarianceSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRecommendationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetThresholdRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetRecommendationServiceTest {

    @Mock private BudgetVarianceService varianceService;
    @Mock private BudgetForecastService forecastService;
    @Mock private BudgetThresholdRepository thresholdRepository;
    @Mock private BudgetRecommendationRepository recommendationRepository;
    @Mock private BudgetEventEmitter emitter;

    private BudgetRecommendationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BudgetRecommendationService(varianceService, forecastService, thresholdRepository,
                recommendationRepository, emitter, new ObjectMapper(), "0.90");
        lenient().when(recommendationRepository.save(any(BudgetRecommendationEntity.class))).thenAnswer(inv -> {
            BudgetRecommendationEntity r = inv.getArgument(0);
            if (r.getRecommendationId() == null) r.setRecommendationId(UUID.randomUUID());
            return r;
        });
        lenient().when(thresholdRepository.findByTenantIdAndBudgetIdAndActiveTrue(tenantId, budgetId))
                .thenReturn(List.of());
        lenient().when(recommendationRepository.findFirstByTenantIdAndBudgetIdAndRuleCodeAndStatus(
                any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private void variance(String budgeted, String committed, String actual, String classification) {
        BudgetVarianceSnapshotEntity v = new BudgetVarianceSnapshotEntity();
        v.setBudgetId(budgetId);
        v.setBudgeted(new BigDecimal(budgeted));
        v.setCommitted(new BigDecimal(committed));
        v.setActual(new BigDecimal(actual));
        v.setClassification(classification);
        when(varianceService.compute(eq(tenantId), eq(budgetId), any())).thenReturn(v);
    }

    private void forecast(String elapsed, String projected, String overrun) {
        BudgetForecastSnapshotEntity f = new BudgetForecastSnapshotEntity();
        f.setMethod("LINEAR_YTD");
        f.setElapsedFraction(new BigDecimal(elapsed));
        f.setProjectedYearEnd(new BigDecimal(projected));
        f.setProjectedOverrun(new BigDecimal(overrun));
        when(forecastService.compute(eq(tenantId), eq(budgetId), eq("LINEAR_YTD"), any())).thenReturn(f);
    }

    @Test void overspendRisk_andThresholdBreach_bothFire() {
        variance("1000", "0", "950", "UNFAVOURABLE_MAJOR");
        forecast("0.5", "1200", "200"); // overrun 200 (>10% of 1000) -> CRITICAL

        List<BudgetRecommendationEntity> raised = service.evaluate(tenantId, budgetId, null);

        assertTrue(raised.stream().anyMatch(r -> r.getRuleCode().equals("OVERSPEND_RISK")
                && r.getSeverity().equals("CRITICAL")));
        assertTrue(raised.stream().anyMatch(r -> r.getRuleCode().equals("THRESHOLD_BREACH")));
        verify(emitter, atLeastOnce()).emit(eq("BUDGET_RECOMMENDATION"), any(),
                eq("BUDGET_RECOMMENDATION_RAISED"), eq(tenantId), any());
    }

    @Test void underabsorption_firesFromStaleClassification() {
        variance("1000", "0", "100", "UNDERSPENT_STALE");
        forecast("0.5", "200", "-800"); // no overrun, low utilisation

        List<BudgetRecommendationEntity> raised = service.evaluate(tenantId, budgetId, null);

        assertTrue(raised.stream().anyMatch(r -> r.getRuleCode().equals("UNDERABSORPTION")));
        assertTrue(raised.stream().noneMatch(r -> r.getRuleCode().equals("OVERSPEND_RISK")));
    }

    @Test void noDoubleRaise_whenOpenRecommendationExists() {
        variance("1000", "0", "950", "UNFAVOURABLE_MAJOR");
        forecast("0.5", "1200", "200");
        when(recommendationRepository.findFirstByTenantIdAndBudgetIdAndRuleCodeAndStatus(
                tenantId, budgetId, "OVERSPEND_RISK", "OPEN"))
                .thenReturn(Optional.of(new BudgetRecommendationEntity()));

        List<BudgetRecommendationEntity> raised = service.evaluate(tenantId, budgetId, null);
        assertTrue(raised.stream().noneMatch(r -> r.getRuleCode().equals("OVERSPEND_RISK")));
    }

    @Test void earlyPeriod_noOverspendRisk() {
        variance("1000", "0", "300", "ON_TRACK");
        forecast("0.10", "3000", "2000"); // elapsed < 0.25 -> R1 suppressed

        List<BudgetRecommendationEntity> raised = service.evaluate(tenantId, budgetId, null);
        assertTrue(raised.stream().noneMatch(r -> r.getRuleCode().equals("OVERSPEND_RISK")));
    }
}
