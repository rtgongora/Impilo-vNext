package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetForecastSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetRecommendationEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetThresholdEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVarianceSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.RecommendationSeverity;
import zw.gov.mohcc.impilo.costa.domain.enums.RecommendationStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.VarianceClassification;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRecommendationRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetThresholdRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule-based, explainable budget recommendations. Each rule fires from real
 * variance / forecast numbers and records its drivers to {@code evidenceJson}.
 * No model inference, no fake AI. Recommendations are de-duplicated against
 * existing OPEN ones for the same rule.
 */
@Service
public class BudgetRecommendationService {

    private final BudgetVarianceService varianceService;
    private final BudgetForecastService forecastService;
    private final BudgetThresholdRepository thresholdRepository;
    private final BudgetRecommendationRepository recommendationRepository;
    private final BudgetEventEmitter emitter;
    private final ObjectMapper objectMapper;
    private final BigDecimal defaultWarnFraction;

    public BudgetRecommendationService(BudgetVarianceService varianceService,
                                       BudgetForecastService forecastService,
                                       BudgetThresholdRepository thresholdRepository,
                                       BudgetRecommendationRepository recommendationRepository,
                                       BudgetEventEmitter emitter,
                                       ObjectMapper objectMapper,
                                       @Value("${costa.budget.warn-utilisation:0.90}") String defaultWarnFraction) {
        this.varianceService = varianceService;
        this.forecastService = forecastService;
        this.thresholdRepository = thresholdRepository;
        this.recommendationRepository = recommendationRepository;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.defaultWarnFraction = new BigDecimal(defaultWarnFraction);
    }

    @Transactional
    public List<BudgetRecommendationEntity> evaluate(UUID tenantId, UUID budgetId, LocalDate asOf) {
        BudgetVarianceSnapshotEntity var = varianceService.compute(tenantId, budgetId, asOf);
        BudgetForecastSnapshotEntity fc = forecastService.compute(tenantId, budgetId, "LINEAR_YTD", asOf);

        BigDecimal budgeted = var.getBudgeted();
        BigDecimal actual = var.getActual();
        BigDecimal committed = var.getCommitted();
        BigDecimal elapsed = fc.getElapsedFraction() != null ? fc.getElapsedFraction() : BigDecimal.ZERO;
        BigDecimal overrun = fc.getProjectedOverrun() != null ? fc.getProjectedOverrun() : BigDecimal.ZERO;

        List<BudgetRecommendationEntity> raised = new ArrayList<>();
        BigDecimal quarter = new BigDecimal("0.25");

        // R1 OVERSPEND_RISK — projected to exceed budget after 25% of the period
        if (budgeted.signum() > 0 && overrun.signum() > 0 && elapsed.compareTo(quarter) >= 0) {
            BigDecimal overrunPct = overrun.divide(budgeted, 4, RoundingMode.HALF_UP);
            RecommendationSeverity sev = overrunPct.compareTo(new BigDecimal("0.10")) > 0
                    ? RecommendationSeverity.CRITICAL : RecommendationSeverity.WARN;
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("budgeted", budgeted);
            ev.put("projectedYearEnd", fc.getProjectedYearEnd());
            ev.put("projectedOverrun", overrun);
            ev.put("elapsedFraction", elapsed);
            raise(raised, tenantId, budgetId, "OVERSPEND_RISK", sev,
                    "Projected to exceed budget by period end",
                    "Forecast overrun of " + overrun.toPlainString() + " at " + fc.getMethod(),
                    "Accelerate reprogramming approval or reduce planned commitments", ev);
        }

        // R2 UNDERABSORPTION — significantly behind the elapsed-time spend pace
        if (VarianceClassification.UNDERSPENT_STALE.name().equals(var.getClassification())) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("budgeted", budgeted);
            ev.put("actual", actual);
            ev.put("elapsedFraction", elapsed);
            ev.put("driverNote", var.getDriverNote());
            raise(raised, tenantId, budgetId, "UNDERABSORPTION", RecommendationSeverity.WARN,
                    "Underabsorption — spend is behind schedule",
                    "Actual expenditure is well below the elapsed-time pace; funds may be at risk",
                    "Investigate delayed execution; consider reprogramming or implementation support", ev);
        }

        // R4 THRESHOLD_BREACH — committed+actual utilisation crosses a threshold
        if (budgeted.signum() > 0) {
            BigDecimal utilisation = committed.add(actual).divide(budgeted, 4, RoundingMode.HALF_UP);
            BigDecimal warnAt = defaultWarnFraction;
            BigDecimal hardAt = BigDecimal.ONE;
            List<BudgetThresholdEntity> ts = thresholdRepository.findByTenantIdAndBudgetIdAndActiveTrue(tenantId, budgetId);
            for (BudgetThresholdEntity t : ts) {
                if ("UTILISATION_PCT".equals(t.getMetric())) {
                    warnAt = t.getWarnAt();
                    if (t.getHardStopAt() != null) hardAt = t.getHardStopAt();
                }
            }
            if (utilisation.compareTo(warnAt) >= 0) {
                RecommendationSeverity sev = utilisation.compareTo(hardAt) >= 0
                        ? RecommendationSeverity.CRITICAL : RecommendationSeverity.WARN;
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("utilisation", utilisation);
                ev.put("warnAt", warnAt);
                ev.put("hardStopAt", hardAt);
                ev.put("committed", committed);
                ev.put("actual", actual);
                ev.put("budgeted", budgeted);
                raise(raised, tenantId, budgetId, "THRESHOLD_BREACH", sev,
                        "Utilisation threshold reached",
                        "Committed + actual utilisation is "
                                + utilisation.movePointRight(2).stripTrailingZeros().toPlainString() + "%",
                        "Review remaining commitments and available balance", ev);
            }
        }
        return raised;
    }

    private void raise(List<BudgetRecommendationEntity> out, UUID tenantId, UUID budgetId,
                       String ruleCode, RecommendationSeverity severity, String title,
                       String rationale, String suggestedAction, Map<String, Object> evidence) {
        // de-dup against an existing OPEN recommendation for the same rule
        if (recommendationRepository.findFirstByTenantIdAndBudgetIdAndRuleCodeAndStatus(
                tenantId, budgetId, ruleCode, RecommendationStatus.OPEN.name()).isPresent()) {
            return;
        }
        BudgetRecommendationEntity r = new BudgetRecommendationEntity();
        r.setBudgetId(budgetId);
        r.setTenantId(tenantId);
        r.setRuleCode(ruleCode);
        r.setSeverity(severity.name());
        r.setTitle(title);
        r.setRationale(rationale);
        r.setSuggestedAction(suggestedAction);
        r.setStatus(RecommendationStatus.OPEN.name());
        try {
            r.setEvidenceJson(objectMapper.writeValueAsString(evidence));
        } catch (Exception e) {
            r.setEvidenceJson("{}");
        }
        recommendationRepository.save(r);
        emitter.emit("BUDGET_RECOMMENDATION", r.getRecommendationId().toString(),
                "BUDGET_RECOMMENDATION_RAISED", tenantId, Map.of(
                        "recommendationId", r.getRecommendationId().toString(),
                        "budgetId", budgetId.toString(),
                        "ruleCode", ruleCode,
                        "severity", severity.name()));
        out.add(r);
    }

    public List<BudgetRecommendationEntity> list(UUID tenantId, UUID budgetId) {
        return recommendationRepository.findByBudgetIdAndTenantIdOrderByCreatedAtDesc(budgetId, tenantId);
    }

    @Transactional
    public BudgetRecommendationEntity setStatus(UUID tenantId, UUID recommendationId, String status) {
        RecommendationStatus.valueOf(status);
        BudgetRecommendationEntity r = recommendationRepository
                .findByRecommendationIdAndTenantId(recommendationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found"));
        r.setStatus(status);
        return recommendationRepository.save(r);
    }
}
