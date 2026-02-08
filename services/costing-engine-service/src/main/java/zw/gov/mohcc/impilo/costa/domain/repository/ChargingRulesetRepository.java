package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargingRulesetEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.RulesetStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChargingRulesetRepository extends JpaRepository<ChargingRulesetEntity, Long> {

    Page<ChargingRulesetEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT r FROM ChargingRulesetEntity r WHERE r.tenantId = :tenantId " +
           "AND r.status = 'PUBLISHED' " +
           "AND r.effectiveFrom <= :asOf AND (r.effectiveTo IS NULL OR r.effectiveTo > :asOf) " +
           "ORDER BY r.effectiveFrom DESC")
    List<ChargingRulesetEntity> findEffective(@Param("tenantId") UUID tenantId,
                                              @Param("asOf") LocalDate asOf);

    List<ChargingRulesetEntity> findByTenantIdAndStatus(UUID tenantId, RulesetStatus status);
}
