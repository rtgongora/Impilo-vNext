package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.clinical.persistence.entity.NationalPolicyParameterEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface NationalPolicyParameterRepository extends JpaRepository<NationalPolicyParameterEntity, UUID> {

    /**
     * The versions of a parameter in force on a date, newest window first. A caller asking about a
     * past act gets the version that governed then — the whole point of the effective window.
     * WITHDRAWN rows are excluded: a withdrawn parameter is not in force at any date.
     */
    @Query("SELECT p FROM NationalPolicyParameterEntity p "
            + "WHERE p.parameterCode = :code "
            + "AND p.approvalStatus <> 'WITHDRAWN' "
            + "AND p.effectiveStart <= :asOf "
            + "AND (p.effectiveEnd IS NULL OR p.effectiveEnd > :asOf) "
            + "ORDER BY p.effectiveStart DESC")
    List<NationalPolicyParameterEntity> findInForce(@Param("code") String code, @Param("asOf") LocalDate asOf);
}
