package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetForecastSnapshotEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetForecastSnapshotRepository extends JpaRepository<BudgetForecastSnapshotEntity, Long> {

    List<BudgetForecastSnapshotEntity> findByBudgetIdAndTenantIdOrderByAsOfDateDescCreatedAtDesc(
            UUID budgetId, UUID tenantId);
}
