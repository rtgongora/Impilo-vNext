package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.InsurancePlanEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsurancePlanRepository extends JpaRepository<InsurancePlanEntity, Long> {

    @Query("SELECT p FROM InsurancePlanEntity p WHERE p.tenantId = :tenantId " +
           "AND p.planCode = :planCode AND p.status = 'ACTIVE' " +
           "AND p.effectiveFrom <= :asOf AND (p.effectiveTo IS NULL OR p.effectiveTo > :asOf)")
    List<InsurancePlanEntity> findEffective(@Param("tenantId") UUID tenantId,
                                            @Param("planCode") String planCode,
                                            @Param("asOf") LocalDate asOf);

    Optional<InsurancePlanEntity> findByTenantIdAndPlanCodeAndStatus(UUID tenantId, String planCode, String status);
    List<InsurancePlanEntity> findByTenantIdAndStatus(UUID tenantId, String status);
    List<InsurancePlanEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<InsurancePlanEntity> findByTenantIdAndPlanCode(UUID tenantId, String planCode);
}
