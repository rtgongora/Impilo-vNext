package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetPeriodCloseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetPeriodCloseRepository extends JpaRepository<BudgetPeriodCloseEntity, Long> {
    Optional<BudgetPeriodCloseEntity> findByCloseIdAndTenantId(UUID closeId, UUID tenantId);
    Optional<BudgetPeriodCloseEntity> findByTenantIdAndBudgetIdAndFinancialPeriodId(
            UUID tenantId, UUID budgetId, UUID financialPeriodId);
    List<BudgetPeriodCloseEntity> findByBudgetIdAndTenantIdOrderByCreatedAtDesc(UUID budgetId, UUID tenantId);
}
