package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkforceAssignmentRepository extends JpaRepository<WorkforceAssignmentEntity, UUID> {

    List<WorkforceAssignmentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WorkforceAssignmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<WorkforceAssignmentEntity> findByTenantIdAndWorkforceProfileIdOrderByCreatedAtDesc(
            UUID tenantId, UUID workforceProfileId);

    List<WorkforceAssignmentEntity> findByTenantIdAndFacilityIdAndStatus(
            UUID tenantId, UUID facilityId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    List<WorkforceAssignmentEntity> findByTenantIdAndWorkforceProfileIdAndStatusIn(
            UUID tenantId, UUID workforceProfileId, List<String> statuses);
}
