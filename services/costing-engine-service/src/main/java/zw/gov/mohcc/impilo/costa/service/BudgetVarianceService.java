package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVarianceSnapshotEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVersionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.VarianceClassification;
import zw.gov.mohcc.impilo.costa.domain.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Computes budget-vs-actual variance and an explainable classification.
 * budgeted = current active version total; committed = outstanding commitments;
 * actual = posted actual references; available = budgeted − committed − actual.
 * Classification compares spend pace to elapsed time — fully transparent.
 */
@Service
public class BudgetVarianceService {

    private final BudgetRepository budgetRepository;
    private final BudgetVersionRepository versionRepository;
    private final BudgetCommitmentRepository commitmentRepository;
    private final BudgetActualReferenceRepository actualRepository;
    private final BudgetVarianceSnapshotRepository snapshotRepository;

    public BudgetVarianceService(BudgetRepository budgetRepository,
                                 BudgetVersionRepository versionRepository,
                                 BudgetCommitmentRepository commitmentRepository,
                                 BudgetActualReferenceRepository actualRepository,
                                 BudgetVarianceSnapshotRepository snapshotRepository) {
        this.budgetRepository = budgetRepository;
        this.versionRepository = versionRepository;
        this.commitmentRepository = commitmentRepository;
        this.actualRepository = actualRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /** Compute a live (unsaved) variance snapshot for a budget. */
    public BudgetVarianceSnapshotEntity compute(UUID tenantId, UUID budgetId, LocalDate asOf) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        LocalDate as = asOf != null ? asOf : LocalDate.now();

        BigDecimal budgeted = versionRepository.findByBudgetIdAndTenantIdAndCurrentTrue(budgetId, tenantId)
                .map(BudgetVersionEntity::getTotalAmount).orElse(BigDecimal.ZERO);
        BigDecimal committed = nz(commitmentRepository.sumOutstandingByBudget(tenantId, budgetId));
        BigDecimal actual = nz(actualRepository.sumActualByBudget(tenantId, budgetId));
        BigDecimal available = budgeted.subtract(committed).subtract(actual);
        BigDecimal varianceAmount = budgeted.subtract(actual);
        BigDecimal variancePct = budgeted.signum() > 0
                ? varianceAmount.divide(budgeted, 6, RoundingMode.HALF_UP).movePointRight(2)
                : null;

        LocalDate start = BudgetMath.periodStart(b.getPeriodStart(), b.getPeriodYear());
        LocalDate end = BudgetMath.periodEnd(b.getPeriodEnd(), b.getPeriodYear());
        BigDecimal elapsed = BudgetMath.elapsedFraction(start, end, as);
        BigDecimal expectedByElapsed = budgeted.multiply(elapsed).setScale(2, RoundingMode.HALF_UP);
        VarianceClassification classification = classify(budgeted, actual, elapsed);

        BudgetVarianceSnapshotEntity s = new BudgetVarianceSnapshotEntity();
        s.setBudgetId(budgetId);
        s.setTenantId(tenantId);
        s.setAsOfDate(as);
        s.setPeriodScope("YTD");
        s.setBudgeted(budgeted);
        s.setCommitted(committed);
        s.setActual(actual);
        s.setAvailable(available);
        s.setVarianceAmount(varianceAmount);
        s.setVariancePct(variancePct);
        s.setClassification(classification.name());
        s.setDriverNote("budgeted=" + budgeted.toPlainString()
                + "; committed=" + committed.toPlainString()
                + "; actual=" + actual.toPlainString()
                + "; expectedByElapsed=" + expectedByElapsed.toPlainString()
                + "; elapsedFraction=" + elapsed.toPlainString());
        return s;
    }

    @Transactional
    public BudgetVarianceSnapshotEntity snapshot(UUID tenantId, UUID budgetId, LocalDate asOf) {
        return snapshotRepository.save(compute(tenantId, budgetId, asOf));
    }

    public List<BudgetVarianceSnapshotEntity> history(UUID tenantId, UUID budgetId) {
        return snapshotRepository.findByBudgetIdAndTenantIdOrderByAsOfDateDescCreatedAtDesc(budgetId, tenantId);
    }

    /** Transparent classification: overspend, or spend-pace vs elapsed time. */
    static VarianceClassification classify(BigDecimal budgeted, BigDecimal actual, BigDecimal elapsedFraction) {
        if (budgeted.signum() <= 0) return VarianceClassification.ON_TRACK;
        if (actual.compareTo(budgeted) > 0) return VarianceClassification.OVERSPENT;

        BigDecimal expected = budgeted.multiply(elapsedFraction);
        BigDecimal pace = actual.subtract(expected); // >0 = spending faster than time
        BigDecimal pacePct = pace.divide(budgeted, 6, RoundingMode.HALF_UP);

        BigDecimal fivePct = new BigDecimal("0.05");
        BigDecimal fifteenPct = new BigDecimal("0.15");
        BigDecimal minusTwentyPct = new BigDecimal("-0.20");
        BigDecimal quarter = new BigDecimal("0.25");

        if (pacePct.abs().compareTo(fivePct) < 0) return VarianceClassification.ON_TRACK;
        if (pacePct.compareTo(minusTwentyPct) <= 0 && elapsedFraction.compareTo(quarter) >= 0) {
            return VarianceClassification.UNDERSPENT_STALE;
        }
        if (pacePct.signum() < 0) return VarianceClassification.FAVOURABLE;
        if (pacePct.compareTo(fifteenPct) >= 0) return VarianceClassification.UNFAVOURABLE_MAJOR;
        return VarianceClassification.UNFAVOURABLE_MINOR;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
