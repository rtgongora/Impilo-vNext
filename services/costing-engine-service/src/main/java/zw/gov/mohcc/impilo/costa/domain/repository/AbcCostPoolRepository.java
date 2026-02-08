package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcCostPoolEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AbcCostPoolRepository extends JpaRepository<AbcCostPoolEntity, Long> {

    @Query("SELECT p FROM AbcCostPoolEntity p WHERE p.tenantId = :tenantId " +
           "AND (p.facilityId = :facilityId OR p.facilityId IS NULL) " +
           "AND p.effectiveFrom <= :asOf AND (p.effectiveTo IS NULL OR p.effectiveTo > :asOf)")
    List<AbcCostPoolEntity> findEffectivePools(@Param("tenantId") UUID tenantId,
                                               @Param("facilityId") UUID facilityId,
                                               @Param("asOf") LocalDate asOf);

    List<AbcCostPoolEntity> findByTenantId(UUID tenantId);
}
