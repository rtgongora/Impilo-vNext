package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.AbcDriverRateEntity;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AbcDriverRateRepository extends JpaRepository<AbcDriverRateEntity, Long> {

    @Query("SELECT d FROM AbcDriverRateEntity d WHERE d.poolId = :poolId " +
           "AND d.effectiveFrom <= :asOf AND (d.effectiveTo IS NULL OR d.effectiveTo > :asOf) " +
           "ORDER BY d.effectiveFrom DESC")
    List<AbcDriverRateEntity> findEffectiveRates(@Param("poolId") Long poolId,
                                                  @Param("asOf") LocalDate asOf);

    List<AbcDriverRateEntity> findByPoolId(Long poolId);
}
