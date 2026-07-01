package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetRevisionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRevisionRepository extends JpaRepository<BudgetRevisionEntity, Long> {
    Optional<BudgetRevisionEntity> findByRevisionIdAndTenantId(UUID revisionId, UUID tenantId);
    List<BudgetRevisionEntity> findByBudgetIdAndTenantIdOrderByCreatedAtDesc(UUID budgetId, UUID tenantId);
}
