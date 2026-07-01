package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetAllocationEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocationEntity, Long> {

    List<BudgetAllocationEntity> findByTenantIdAndFacilityIdAndPeriodYearOrderByBudgetCategory(
            UUID tenantId, UUID facilityId, int periodYear);

    Optional<BudgetAllocationEntity> findByIdAndTenantId(long id, UUID tenantId);

    Optional<BudgetAllocationEntity> findByAllocationIdAndTenantId(UUID allocationId, UUID tenantId);

    @Query("""
            SELECT b FROM BudgetAllocationEntity b
            WHERE b.tenantId = :tenantId AND b.facilityId = :facilityId AND b.periodYear = :periodYear
            AND b.budgetCategory = :budgetCategory
            AND ((:departmentId IS NULL AND b.departmentId IS NULL) OR b.departmentId = :departmentId)
            """)
    Optional<BudgetAllocationEntity> findForSpendLine(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId,
            @Param("departmentId") String departmentId,
            @Param("budgetCategory") String budgetCategory,
            @Param("periodYear") int periodYear);

    @Query("""
            SELECT COALESCE(SUM(b.spentAmount), 0) FROM BudgetAllocationEntity b
            WHERE b.tenantId = :tenantId AND b.facilityId = :facilityId AND b.periodYear = :year""")
    BigDecimal sumSpentByFacilityYear(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId,
            @Param("year") int year);
}
