package zw.gov.mohcc.impilo.costa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.AvailabilityDecision;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetActualReferenceRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetCommitmentRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetLineRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Budget availability control. Computes available = allocated − outstanding
 * commitments − actuals for a line and decides ALLOW / WARN / HARD_STOP /
 * EMERGENCY_OVERRIDE. Clinical emergencies are never HARD_STOPped — they are
 * allowed and audited as EMERGENCY_OVERRIDE when they exceed the budget.
 */
@Service
public class BudgetAvailabilityService {

    private final BudgetLineRepository lineRepository;
    private final BudgetCommitmentRepository commitmentRepository;
    private final BudgetActualReferenceRepository actualRepository;
    private final BudgetEventEmitter emitter;
    private final BigDecimal warnFraction;

    public BudgetAvailabilityService(BudgetLineRepository lineRepository,
                                     BudgetCommitmentRepository commitmentRepository,
                                     BudgetActualReferenceRepository actualRepository,
                                     BudgetEventEmitter emitter,
                                     @Value("${costa.budget.warn-utilisation:0.90}") String warnFraction) {
        this.lineRepository = lineRepository;
        this.commitmentRepository = commitmentRepository;
        this.actualRepository = actualRepository;
        this.emitter = emitter;
        this.warnFraction = new BigDecimal(warnFraction);
    }

    public record AvailabilityResult(
            AvailabilityDecision decision, UUID budgetLineId,
            BigDecimal allocated, BigDecimal committed, BigDecimal actual, BigDecimal available,
            BigDecimal requested, BigDecimal projectedUtilisation, boolean emergency, String reason) {}

    @Transactional
    public AvailabilityResult check(UUID tenantId, UUID budgetLineId, BigDecimal requested,
                                    boolean emergency, boolean clinicalEmergency) {
        BudgetLineEntity line = lineRepository.findByLineIdAndTenantId(budgetLineId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget line not found"));
        BigDecimal req = requested != null ? requested : BigDecimal.ZERO;
        if (req.signum() < 0) throw new IllegalArgumentException("requested amount must not be negative");

        BigDecimal allocated = nz(line.getAllocatedAmount());
        BigDecimal committed = nz(commitmentRepository.sumOutstandingByLine(tenantId, budgetLineId));
        BigDecimal actual = nz(actualRepository.sumActualByLine(tenantId, budgetLineId));
        BigDecimal available = allocated.subtract(committed).subtract(actual);

        BigDecimal projected = committed.add(actual).add(req);
        BigDecimal utilisation = allocated.signum() > 0
                ? projected.divide(allocated, 4, RoundingMode.HALF_UP)
                : (projected.signum() > 0 ? new BigDecimal("999") : BigDecimal.ZERO);

        AvailabilityDecision decision;
        String reason;
        boolean withinBudget = req.compareTo(available) <= 0;

        if (withinBudget) {
            if (utilisation.compareTo(warnFraction) >= 0) {
                decision = AvailabilityDecision.WARN;
                reason = "Within budget but projected utilisation "
                        + utilisation.movePointRight(2).stripTrailingZeros().toPlainString()
                        + "% reaches the warning threshold";
            } else {
                decision = AvailabilityDecision.ALLOW;
                reason = "Sufficient available balance";
            }
        } else if (clinicalEmergency || emergency) {
            decision = AvailabilityDecision.EMERGENCY_OVERRIDE;
            reason = (clinicalEmergency ? "Clinical emergency" : "Emergency")
                    + " override: request exceeds available balance and is allowed with audit";
            emitter.emit("BUDGET_AVAILABILITY", budgetLineId.toString(),
                    "BUDGET_AVAILABILITY_OVERRIDE", tenantId,
                    overridePayload(line, allocated, committed, actual, available, req, clinicalEmergency));
        } else {
            decision = AvailabilityDecision.HARD_STOP;
            reason = "Request of " + req.toPlainString() + " exceeds available balance of "
                    + available.toPlainString();
        }

        return new AvailabilityResult(decision, budgetLineId, allocated, committed, actual,
                available, req, utilisation, clinicalEmergency || emergency, reason);
    }

    private Map<String, Object> overridePayload(BudgetLineEntity line, BigDecimal allocated,
                                                BigDecimal committed, BigDecimal actual,
                                                BigDecimal available, BigDecimal requested,
                                                boolean clinicalEmergency) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetId", line.getBudgetId().toString());
        m.put("budgetLineId", line.getLineId().toString());
        m.put("tenantId", line.getTenantId().toString());
        m.put("allocated", allocated);
        m.put("committed", committed);
        m.put("actual", actual);
        m.put("available", available);
        m.put("requested", requested);
        m.put("clinicalEmergency", clinicalEmergency);
        return m;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
