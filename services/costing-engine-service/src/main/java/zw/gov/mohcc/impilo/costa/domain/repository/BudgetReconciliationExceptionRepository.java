package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetReconciliationExceptionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetReconciliationExceptionRepository extends JpaRepository<BudgetReconciliationExceptionEntity, Long> {
    Optional<BudgetReconciliationExceptionEntity> findByExceptionIdAndTenantId(UUID exceptionId, UUID tenantId);
    List<BudgetReconciliationExceptionEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    List<BudgetReconciliationExceptionEntity> findByBudgetIdAndTenantIdOrderByCreatedAtDesc(UUID budgetId, UUID tenantId);
}
