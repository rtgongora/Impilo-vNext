package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetPeriodCloseEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BudgetStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.PeriodCloseStatus;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetPeriodCloseRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Closes a budget against a financial period (soft/hard) with optional
 * carry-forward, and reopens when permitted. A HARD_CLOSED budget is also moved
 * to the CLOSED lifecycle state.
 */
@Service
public class BudgetPeriodCloseService {

    private final BudgetPeriodCloseRepository repository;
    private final BudgetRepository budgetRepository;
    private final BudgetEventEmitter emitter;

    public BudgetPeriodCloseService(BudgetPeriodCloseRepository repository,
                                    BudgetRepository budgetRepository,
                                    BudgetEventEmitter emitter) {
        this.repository = repository;
        this.budgetRepository = budgetRepository;
        this.emitter = emitter;
    }

    public record CloseRequest(UUID financialPeriodId, String status,
                               BigDecimal carryForwardAmount, String notes) {}

    @Transactional
    public BudgetPeriodCloseEntity close(UUID tenantId, UUID budgetId, String actorId, CloseRequest cmd) {
        BudgetEntity b = budgetRepository.findByBudgetIdAndTenantId(budgetId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found"));
        if (cmd.financialPeriodId() == null) {
            throw new IllegalArgumentException("financialPeriodId is required");
        }
        PeriodCloseStatus target = cmd.status() != null
                ? PeriodCloseStatus.valueOf(cmd.status()) : PeriodCloseStatus.SOFT_CLOSED;
        if (target == PeriodCloseStatus.OPEN || target == PeriodCloseStatus.REOPENED) {
            throw new IllegalArgumentException("Use SOFT_CLOSED or HARD_CLOSED to close");
        }

        BudgetPeriodCloseEntity e = repository
                .findByTenantIdAndBudgetIdAndFinancialPeriodId(tenantId, budgetId, cmd.financialPeriodId())
                .orElseGet(BudgetPeriodCloseEntity::new);
        e.setTenantId(tenantId);
        e.setBudgetId(budgetId);
        e.setFinancialPeriodId(cmd.financialPeriodId());
        e.setStatus(target.name());
        e.setClosedBy(actorId);
        e.setClosedAt(OffsetDateTime.now());
        e.setCarryForwardAmount(cmd.carryForwardAmount());
        e.setNotes(cmd.notes());
        repository.save(e);

        if (target == PeriodCloseStatus.HARD_CLOSED
                && (BudgetStatus.ACTIVE.name().equals(b.getStatus())
                    || BudgetStatus.FROZEN.name().equals(b.getStatus()))) {
            b.setStatus(BudgetStatus.CLOSED.name());
            budgetRepository.save(b);
        }
        emitter.emit("BUDGET", budgetId.toString(), "BUDGET_PERIOD_CLOSED", tenantId, payload(e));
        return e;
    }

    @Transactional
    public BudgetPeriodCloseEntity reopen(UUID tenantId, UUID closeId, String actorId, String note) {
        BudgetPeriodCloseEntity e = repository.findByCloseIdAndTenantId(closeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Period close not found"));
        if (PeriodCloseStatus.HARD_CLOSED.name().equals(e.getStatus())) {
            throw new IllegalStateException("A HARD_CLOSED period cannot be reopened");
        }
        e.setStatus(PeriodCloseStatus.REOPENED.name());
        e.setNotes(note);
        e.setClosedBy(actorId);
        return repository.save(e);
    }

    public List<BudgetPeriodCloseEntity> list(UUID tenantId, UUID budgetId) {
        return repository.findByBudgetIdAndTenantIdOrderByCreatedAtDesc(budgetId, tenantId);
    }

    private Map<String, Object> payload(BudgetPeriodCloseEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("closeId", e.getCloseId().toString());
        m.put("budgetId", e.getBudgetId().toString());
        m.put("tenantId", e.getTenantId().toString());
        m.put("financialPeriodId", e.getFinancialPeriodId().toString());
        m.put("status", e.getStatus());
        m.put("carryForwardAmount", e.getCarryForwardAmount());
        return m;
    }
}
