package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetReconciliationExceptionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationExceptionStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationExceptionType;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetReconciliationExceptionRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records and resolves budget reconciliation exceptions (unmatched/mismatched actuals). */
@Service
public class BudgetReconciliationService {

    private final BudgetReconciliationExceptionRepository repository;
    private final BudgetEventEmitter emitter;

    public BudgetReconciliationService(BudgetReconciliationExceptionRepository repository,
                                       BudgetEventEmitter emitter) {
        this.repository = repository;
        this.emitter = emitter;
    }

    public record NewException(UUID budgetId, UUID budgetLineId, String exceptionType, String source,
                               String sourceReference, BigDecimal expected, BigDecimal observed) {}

    @Transactional
    public BudgetReconciliationExceptionEntity open(UUID tenantId, NewException cmd) {
        ReconciliationExceptionType.valueOf(cmd.exceptionType()); // validate
        BudgetReconciliationExceptionEntity e = new BudgetReconciliationExceptionEntity();
        e.setTenantId(tenantId);
        e.setBudgetId(cmd.budgetId());
        e.setBudgetLineId(cmd.budgetLineId());
        e.setExceptionType(cmd.exceptionType());
        e.setSource(cmd.source());
        e.setSourceReference(cmd.sourceReference());
        e.setExpected(cmd.expected());
        e.setObserved(cmd.observed());
        if (cmd.expected() != null && cmd.observed() != null) {
            e.setDelta(cmd.observed().subtract(cmd.expected()));
        }
        e.setStatus(ReconciliationExceptionStatus.OPEN.name());
        repository.save(e);
        emitter.emit("BUDGET_RECON", e.getExceptionId().toString(), "BUDGET_RECON_EXCEPTION_OPENED",
                tenantId, payload(e));
        return e;
    }

    public List<BudgetReconciliationExceptionEntity> listOpen(UUID tenantId) {
        return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                tenantId, ReconciliationExceptionStatus.OPEN.name());
    }

    public List<BudgetReconciliationExceptionEntity> listForBudget(UUID tenantId, UUID budgetId) {
        return repository.findByBudgetIdAndTenantIdOrderByCreatedAtDesc(budgetId, tenantId);
    }

    @Transactional
    public BudgetReconciliationExceptionEntity resolve(UUID tenantId, UUID exceptionId, String status, String note) {
        ReconciliationExceptionStatus target = ReconciliationExceptionStatus.valueOf(status);
        if (target == ReconciliationExceptionStatus.OPEN) {
            throw new IllegalArgumentException("Use INVESTIGATING, RESOLVED or WRITTEN_OFF");
        }
        BudgetReconciliationExceptionEntity e = repository.findByExceptionIdAndTenantId(exceptionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation exception not found"));
        e.setStatus(target.name());
        e.setResolutionNote(note);
        return repository.save(e);
    }

    private Map<String, Object> payload(BudgetReconciliationExceptionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exceptionId", e.getExceptionId().toString());
        m.put("tenantId", e.getTenantId().toString());
        m.put("exceptionType", e.getExceptionType());
        m.put("source", e.getSource());
        m.put("sourceReference", e.getSourceReference());
        if (e.getBudgetId() != null) m.put("budgetId", e.getBudgetId().toString());
        return m;
    }
}
