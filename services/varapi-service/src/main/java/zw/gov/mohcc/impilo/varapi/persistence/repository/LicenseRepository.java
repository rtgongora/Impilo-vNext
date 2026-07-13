package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<LicenseEntity, Long> {

    List<LicenseEntity> findByProviderId(Long providerId);

    List<LicenseEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<LicenseEntity> findByProviderIdOrderByCreatedAtDesc(Long providerId);

    @Query("SELECT l FROM LicenseEntity l WHERE l.provider.id = :providerId " +
           "AND l.status = 'ACTIVE' AND (l.validTo IS NULL OR l.validTo >= CURRENT_DATE)")
    Optional<LicenseEntity> findActiveByProviderId(@Param("providerId") Long providerId);

    /** Active licences expiring within a window — drives the DUE_FOR_RENEWAL sweep. */
    List<LicenseEntity> findByStatusAndValidToBetween(String status, LocalDate from, LocalDate to);

    /** Active licences already past expiry — drives the LAPSED sweep. */
    List<LicenseEntity> findByStatusAndValidToBefore(String status, LocalDate date);
}
