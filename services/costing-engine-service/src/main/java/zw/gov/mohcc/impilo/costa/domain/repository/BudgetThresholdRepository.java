package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetThresholdEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetThresholdRepository extends JpaRepository<BudgetThresholdEntity, Long> {
    Optional<BudgetThresholdEntity> findByThresholdIdAndTenantId(UUID thresholdId, UUID tenantId);
    List<BudgetThresholdEntity> findByTenantIdAndActiveTrue(UUID tenantId);
    List<BudgetThresholdEntity> findByTenantIdAndBudgetIdAndActiveTrue(UUID tenantId, UUID budgetId);
}
