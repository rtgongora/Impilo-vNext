package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetCommitmentEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetCommitmentRepository extends JpaRepository<BudgetCommitmentEntity, Long> {

    Optional<BudgetCommitmentEntity> findByCommitmentIdAndTenantId(UUID commitmentId, UUID tenantId);

    List<BudgetCommitmentEntity> findByBudgetIdAndTenantIdOrderByCommittedAtDesc(UUID budgetId, UUID tenantId);

    List<BudgetCommitmentEntity> findByBudgetLineIdAndTenantId(UUID budgetLineId, UUID tenantId);

    Optional<BudgetCommitmentEntity> findByTenantIdAndOwnerSystemAndOwnerReference(
            UUID tenantId, String ownerSystem, String ownerReference);

    /** Outstanding (committed - liquidated) for open commitments on a line. */
    @Query("""
            SELECT COALESCE(SUM(c.committedAmount - c.liquidatedAmount), 0)
            FROM BudgetCommitmentEntity c
            WHERE c.tenantId = :tenantId AND c.budgetLineId = :lineId
            AND c.status IN ('PROPOSED','COMMITTED','PARTIALLY_LIQUIDATED')""")
    BigDecimal sumOutstandingByLine(@Param("tenantId") UUID tenantId, @Param("lineId") UUID lineId);

    /** Outstanding across a whole budget. */
    @Query("""
            SELECT COALESCE(SUM(c.committedAmount - c.liquidatedAmount), 0)
            FROM BudgetCommitmentEntity c
            WHERE c.tenantId = :tenantId AND c.budgetId = :budgetId
            AND c.status IN ('PROPOSED','COMMITTED','PARTIALLY_LIQUIDATED')""")
    BigDecimal sumOutstandingByBudget(@Param("tenantId") UUID tenantId, @Param("budgetId") UUID budgetId);
}
