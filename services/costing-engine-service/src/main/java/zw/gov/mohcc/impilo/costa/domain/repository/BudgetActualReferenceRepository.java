package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetActualReferenceEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetActualReferenceRepository extends JpaRepository<BudgetActualReferenceEntity, Long> {

    Optional<BudgetActualReferenceEntity> findByTenantIdAndSourceAndSourceReference(
            UUID tenantId, String source, String sourceReference);

    boolean existsByTenantIdAndSourceAndSourceReference(UUID tenantId, String source, String sourceReference);

    List<BudgetActualReferenceEntity> findByBudgetIdAndTenantIdOrderByPostedAtDesc(UUID budgetId, UUID tenantId);

    List<BudgetActualReferenceEntity> findByBudgetLineIdAndTenantId(UUID budgetLineId, UUID tenantId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN a.reversed = false THEN a.actualAmount ELSE 0 END), 0)
            FROM BudgetActualReferenceEntity a
            WHERE a.tenantId = :tenantId AND a.budgetLineId = :lineId""")
    BigDecimal sumActualByLine(@Param("tenantId") UUID tenantId, @Param("lineId") UUID lineId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN a.reversed = false THEN a.actualAmount ELSE 0 END), 0)
            FROM BudgetActualReferenceEntity a
            WHERE a.tenantId = :tenantId AND a.budgetId = :budgetId""")
    BigDecimal sumActualByBudget(@Param("tenantId") UUID tenantId, @Param("budgetId") UUID budgetId);
}
