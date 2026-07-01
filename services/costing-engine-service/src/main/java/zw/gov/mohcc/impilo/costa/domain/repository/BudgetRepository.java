package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, Long> {

    Optional<BudgetEntity> findByBudgetIdAndTenantId(UUID budgetId, UUID tenantId);

    List<BudgetEntity> findByTenantIdAndPeriodYearOrderByCreatedAtDesc(UUID tenantId, int periodYear);

    List<BudgetEntity> findByTenantIdAndFacilityIdAndPeriodYearOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, int periodYear);

    List<BudgetEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<BudgetEntity> findByStatus(String status);
}
