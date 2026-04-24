package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PractitionerInChargeAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PractitionerInChargeAssignmentRepository extends JpaRepository<PractitionerInChargeAssignmentEntity, Long> {

    List<PractitionerInChargeAssignmentEntity> findByProviderId(Long providerId);

    Optional<PractitionerInChargeAssignmentEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);

    List<PractitionerInChargeAssignmentEntity> findByFacilityId(Long facilityId);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndFacilityId(UUID tenantId, Long facilityId);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<PractitionerInChargeAssignmentEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndProviderIdAndStatus(UUID tenantId, Long providerId, String status);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndProviderIdAndStatusOrderByStartDateDescIdDesc(
            UUID tenantId,
            Long providerId,
            String status);

    Optional<PractitionerInChargeAssignmentEntity> findByFacilityIdAndStatusAndEndDateIsNull(Long facilityId, String status);

    Optional<PractitionerInChargeAssignmentEntity> findByTenantIdAndFacilityIdAndStatusAndEndDateIsNull(
            UUID tenantId,
            Long facilityId,
            String status);

    int countByFacilityIdAndStatusAndEndDateIsNull(Long facilityId, String status);

    List<PractitionerInChargeAssignmentEntity> findByFacilityIdAndStatus(Long facilityId, String status);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, Long facilityId, String status);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndProviderIdOrderByStartDateDescIdDesc(UUID tenantId, Long providerId);

    List<PractitionerInChargeAssignmentEntity> findByTenantIdAndFacilityIdOrderByStartDateDescIdDesc(UUID tenantId, Long facilityId);
}
