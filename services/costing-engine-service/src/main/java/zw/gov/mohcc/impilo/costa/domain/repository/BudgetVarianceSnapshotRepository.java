package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetVarianceSnapshotEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetVarianceSnapshotRepository extends JpaRepository<BudgetVarianceSnapshotEntity, Long> {

    List<BudgetVarianceSnapshotEntity> findByBudgetIdAndTenantIdOrderByAsOfDateDescCreatedAtDesc(
            UUID budgetId, UUID tenantId);
}
