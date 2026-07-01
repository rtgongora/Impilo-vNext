package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetLineEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLineEntity, Long> {

    Optional<BudgetLineEntity> findByLineIdAndTenantId(UUID lineId, UUID tenantId);

    List<BudgetLineEntity> findByVersionIdAndTenantIdOrderByLineOrderAscCreatedAtAsc(UUID versionId, UUID tenantId);

    List<BudgetLineEntity> findByBudgetIdAndTenantId(UUID budgetId, UUID tenantId);
}
