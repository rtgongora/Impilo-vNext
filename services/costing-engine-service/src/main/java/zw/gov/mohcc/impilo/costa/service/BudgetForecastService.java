package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetForecastSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVersionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ForecastMethod;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetActualReferenceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetForecastSnapshotRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetVersionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transparent burn-rate / linear forecasting. Every projection stores its
 * drivers (elapsed fraction, burn rate, actual-to-date) to {@code inputsJson}
 * so the number is fully explainable. No model inference, no fake AI.
 */
@Service
public class BudgetForecastService {

    private final BudgetRepository budgetRepository;
    private final BudgetVersionRepository versionRepository;
    private final BudgetActualReferenceRepository actualRepository;
    private final BudgetForecastSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public BudgetForecastService(BudgetRepository budgetRepository,
                                 BudgetVersionRepository versionRepository,
                                 BudgetActualReferenceRepository actualRepository,
                                 BudgetForecastSnapshotRepository snapshotRepository,
                                 ObjectMapper objectMapper) {
        this.budgetRepository = budgetRepository;
        this.versionRepository = versionRepository;
        this.actualRepository = actualRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    public BudgetForecastSnapshotEntity compute(UUID tenantId, UUID budgetId, String methodName, LocalDate asOf) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        ForecastMethod method = methodName != null ? ForecastMethod.valueOf(methodName) : ForecastMethod.LINEAR_YTD;
        if (method == ForecastMethod.SEASONAL_NAIVE) {
            // deferred depth — fall back to burn-rate rather than fake a seasonal curve
            method = ForecastMethod.BURN_RATE;
        }
        LocalDate as = asOf != null ? asOf : LocalDate.now();

        BigDecimal budgeted = versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId)
                .map(BudgetVersionEntity::getTotalAmount).orElse(BigDecimal.ZERO);
        BigDecimal actual = nz(actualRepository.sumActualByBudget(tenantId, budgetId));

        LocalDate start = BudgetMath.periodStart(b.getPeriodStart(), b.getPeriodYear());
        LocalDate end = BudgetMath.periodEnd(b.getPeriodEnd(), b.getPeriodYear());
        long daysInPeriod = BudgetMath.daysInPeriod(start, end);
        long daysElapsed = BudgetMath.daysElapsed(start, end, as);
        long daysRemaining = Math.max(0, daysInPeriod - daysElapsed);
        BigDecimal elapsedFraction = BudgetMath.elapsedFraction(start, end, as);

        BigDecimal burnRate = daysElapsed > 0
                ? actual.divide(BigDecimal.valueOf(daysElapsed), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal projected;
        if (method == ForecastMethod.BURN_RATE) {
            projected = actual.add(burnRate.multiply(BigDecimal.valueOf(daysRemaining)))
                    .setScale(2, RoundingMode.HALF_UP);
        } else { // LINEAR_YTD
            projected = elapsedFraction.signum() > 0
                    ? actual.divide(elapsedFraction, 2, RoundingMode.HALF_UP)
                    : actual;
        }
        BigDecimal overrun = projected.subtract(budgeted);

        String confidence;
        if (daysElapsed == 0) {
            confidence = "No elapsed time yet — projection is not meaningful";
        } else if (elapsedFraction.compareTo(new BigDecimal("0.25")) < 0) {
            confidence = "Low confidence — less than 25% of the period elapsed";
        } else {
            confidence = "Projection based on " + elapsedFraction.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
                    + "% of period elapsed";
        }

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("method", method.name());
        inputs.put("budgeted", budgeted);
        inputs.put("actualToDate", actual);
        inputs.put("daysInPeriod", daysInPeriod);
        inputs.put("daysElapsed", daysElapsed);
        inputs.put("daysRemaining", daysRemaining);
        inputs.put("elapsedFraction", elapsedFraction);
        inputs.put("burnRatePerDay", burnRate);

        BudgetForecastSnapshotEntity f = new BudgetForecastSnapshotEntity();
        f.setBudgetId(budgetId);
        f.setTenantId(tenantId);
        f.setAsOfDate(as);
        f.setMethod(method.name());
        f.setElapsedFraction(elapsedFraction);
        f.setBurnRate(burnRate);
        f.setProjectedYearEnd(projected);
        f.setProjectedOverrun(overrun);
        f.setConfidenceNote(confidence);
        try {
            f.setInputsJson(objectMapper.writeValueAsString(inputs));
        } catch (Exception e) {
            f.setInputsJson("{}");
        }
        return f;
    }

    @Transactional
    public BudgetForecastSnapshotEntity snapshot(UUID tenantId, UUID budgetId, String method, LocalDate asOf) {
        return snapshotRepository.save(compute(tenantId, budgetId, method, asOf));
    }

    public List<BudgetForecastSnapshotEntity> history(UUID tenantId, UUID budgetId) {
        return snapshotRepository.findByBudgetIdAndTenantIdOrderByAsOfDateDescCreatedAtDesc(budgetId, tenantId);
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
