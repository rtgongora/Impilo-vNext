package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.UnitCostSourceEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface UnitCostSourceRepository extends JpaRepository<UnitCostSourceEntity, Long> {

    @Query("SELECT u FROM UnitCostSourceEntity u WHERE u.tenantId = :tenantId " +
           "AND u.msikaCode = :msikaCode " +
           "AND u.effectiveFrom <= :asOf AND (u.effectiveTo IS NULL OR u.effectiveTo > :asOf) " +
           "ORDER BY u.effectiveFrom DESC")
    List<UnitCostSourceEntity> findEffective(@Param("tenantId") UUID tenantId,
                                             @Param("msikaCode") String msikaCode,
                                             @Param("asOf") LocalDate asOf);
}
