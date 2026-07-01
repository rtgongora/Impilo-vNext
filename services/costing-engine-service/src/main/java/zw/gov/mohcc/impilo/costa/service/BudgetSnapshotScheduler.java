package zw.gov.mohcc.impilo.costa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly sweep over ACTIVE budgets: snapshots variance and forecast and
 * re-evaluates recommendations so risk surfaces without manual polling. Each
 * budget is processed in its own transaction so one failure does not abort the
 * whole sweep.
 */
@Service
@Profile("!test")
public class BudgetSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(BudgetSnapshotScheduler.class);

    private final BudgetRepository budgetRepository;
    private final BudgetVarianceService varianceService;
    private final BudgetForecastService forecastService;
    private final BudgetRecommendationService recommendationService;

    public BudgetSnapshotScheduler(BudgetRepository budgetRepository,
                                   BudgetVarianceService varianceService,
                                   BudgetForecastService forecastService,
                                   BudgetRecommendationService recommendationService) {
        this.budgetRepository = budgetRepository;
        this.varianceService = varianceService;
        this.forecastService = forecastService;
        this.recommendationService = recommendationService;
    }

    @Scheduled(cron = "${costa.budget.snapshot-cron:0 30 1 * * *}")
    public void sweep() {
        List<BudgetEntity> active = budgetRepository.findByStatus(BudgetStatus.ACTIVE.name());
        if (active.isEmpty()) return;
        LocalDate today = LocalDate.now();
        int ok = 0;
        for (BudgetEntity b : active) {
            try {
                varianceService.snapshot(b.getTenantId(), b.getBudgetId(), today);
                forecastService.snapshot(b.getTenantId(), b.getBudgetId(), "LINEAR_YTD", today);
                recommendationService.evaluate(b.getTenantId(), b.getBudgetId(), today);
                ok++;
            } catch (Exception e) {
                log.error("Budget snapshot sweep failed for budget {}", b.getBudgetId(), e);
            }
        }
        log.info("Budget snapshot sweep processed {}/{} active budgets", ok, active.size());
    }
}
