package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ExemptionRuleEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ExemptionCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExemptionRuleRepository extends JpaRepository<ExemptionRuleEntity, Long> {

    @Query("SELECT e FROM ExemptionRuleEntity e WHERE e.tenantId = :tenantId " +
           "AND e.category = :category AND e.status = 'ACTIVE' " +
           "AND e.effectiveFrom <= :asOf AND (e.effectiveTo IS NULL OR e.effectiveTo > :asOf)")
    List<ExemptionRuleEntity> findEffective(@Param("tenantId") UUID tenantId,
                                            @Param("category") ExemptionCategory category,
                                            @Param("asOf") LocalDate asOf);

    List<ExemptionRuleEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
