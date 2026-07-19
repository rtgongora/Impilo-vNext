package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityVerificationCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityVerificationCaseRepository
        extends JpaRepository<FacilityVerificationCaseEntity, Long> {

    Optional<FacilityVerificationCaseEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<FacilityVerificationCaseEntity> findByTenantIdAndFacilityUuidOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityUuid);

    List<FacilityVerificationCaseEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String status);

    boolean existsByTenantIdAndFacilityUuidAndStatus(UUID tenantId, UUID facilityUuid, String status);

    List<FacilityVerificationCaseEntity> findByTenantIdAndFacilityUuidAndStatus(
            UUID tenantId, UUID facilityUuid, String status);
}
