package zw.gov.mohcc.impilo.costa.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetThresholdEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ThresholdMetric;
import zw.gov.mohcc.impilo.costa.domain.repository.BudgetThresholdRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CRUD for budget thresholds that drive availability control and alerts. */
@Service
public class BudgetThresholdService {

    private final BudgetThresholdRepository repository;

    public BudgetThresholdService(BudgetThresholdRepository repository) {
        this.repository = repository;
    }

    public record NewThreshold(UUID budgetId, UUID budgetLineId, String metric,
                               BigDecimal warnAt, BigDecimal hardStopAt, Boolean allowEmergencyOverride) {}

    @Transactional
    public BudgetThresholdEntity create(UUID tenantId, NewThreshold cmd) {
        ThresholdMetric.valueOf(cmd.metric()); // validate
        if (cmd.warnAt() == null) throw new IllegalArgumentException("warnAt is required");
        BudgetThresholdEntity t = new BudgetThresholdEntity();
        t.setTenantId(tenantId);
        t.setBudgetId(cmd.budgetId());
        t.setBudgetLineId(cmd.budgetLineId());
        t.setMetric(cmd.metric());
        t.setWarnAt(cmd.warnAt());
        t.setHardStopAt(cmd.hardStopAt());
        t.setAllowEmergencyOverride(cmd.allowEmergencyOverride() == null || cmd.allowEmergencyOverride());
        t.setActive(true);
        return repository.save(t);
    }

    public List<BudgetThresholdEntity> list(UUID tenantId, UUID budgetId) {
        return budgetId != null
                ? repository.findByTenantIdAndBudgetIdAndActiveTrue(tenantId, budgetId)
                : repository.findByTenantIdAndActiveTrue(tenantId);
    }

    @Transactional
    public BudgetThresholdEntity deactivate(UUID tenantId, UUID thresholdId) {
        BudgetThresholdEntity t = repository.findByThresholdIdAndTenantId(thresholdId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Threshold not found"));
        t.setActive(false);
        return repository.save(t);
    }
}
