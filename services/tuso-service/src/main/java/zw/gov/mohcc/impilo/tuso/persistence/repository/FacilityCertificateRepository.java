package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityCertificateRepository extends JpaRepository<FacilityCertificateEntity, UUID> {
    List<FacilityCertificateEntity> findByFacilityIdOrderByIssueDateDesc(Long facilityId);

    Optional<FacilityCertificateEntity> findFirstByFacilityIdAndStatusOrderByIssueDateDesc(Long facilityId,
                                                                                           FacilityCertificateStatus status);

    @Query("select c from FacilityCertificateEntity c where c.status = :status and c.expiryDate <= :cutoff")
    List<FacilityCertificateEntity> findByStatusAndExpiryBefore(FacilityCertificateStatus status, LocalDate cutoff);
}
