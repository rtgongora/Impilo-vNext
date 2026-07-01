package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetVersionRepository extends JpaRepository<BudgetVersionEntity, Long> {

    Optional<BudgetVersionEntity> findByVersionIdAndTenantId(UUID versionId, UUID tenantId);

    List<BudgetVersionEntity> findByBudgetIdAndTenantIdOrderByVersionNoDesc(UUID budgetId, UUID tenantId);

    Optional<BudgetVersionEntity> findByBudgetIdAndTenantIdAndCurrentTrue(UUID budgetId, UUID tenantId);

    long countByBudgetIdAndTenantId(UUID budgetId, UUID tenantId);
}
