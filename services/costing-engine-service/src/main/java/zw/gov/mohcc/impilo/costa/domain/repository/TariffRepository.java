package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TariffRepository extends JpaRepository<TariffEntity, Long> {

    Page<TariffEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    @Query("SELECT t FROM TariffEntity t WHERE t.tenantId = :tenantId " +
           "AND t.tariffCode = :tariffCode AND t.status = 'ACTIVE' " +
           "AND t.effectiveFrom <= :asOf AND (t.effectiveTo IS NULL OR t.effectiveTo > :asOf) " +
           "ORDER BY t.effectiveFrom DESC")
    List<TariffEntity> findEffectiveTariff(@Param("tenantId") UUID tenantId,
                                           @Param("tariffCode") String tariffCode,
                                           @Param("asOf") LocalDate asOf);

    @Query("SELECT t FROM TariffEntity t WHERE t.tenantId = :tenantId " +
           "AND t.msikaCode = :msikaCode AND t.status = 'ACTIVE' " +
           "AND t.effectiveFrom <= :asOf AND (t.effectiveTo IS NULL OR t.effectiveTo > :asOf) " +
           "ORDER BY t.effectiveFrom DESC")
    List<TariffEntity> findByMsikaCode(@Param("tenantId") UUID tenantId,
                                       @Param("msikaCode") String msikaCode,
                                       @Param("asOf") LocalDate asOf);

    Optional<TariffEntity> findByTenantIdAndTariffCodeAndFacilityCategoryAndPatientCategoryAndStatus(
            UUID tenantId, String tariffCode, String facilityCategory, String patientCategory, String status);
}
