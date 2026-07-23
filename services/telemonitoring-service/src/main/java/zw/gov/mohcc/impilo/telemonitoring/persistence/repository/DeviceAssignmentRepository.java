package zw.gov.mohcc.impilo.telemonitoring.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.DeviceAssignmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignmentEntity, UUID> {

    /**
     * The live (non-terminal) assignment for a device — the assignment-gate lookup.
     * {@code deviceActiveGuard} mirrors {@code deviceRef} exactly while the assignment is
     * non-terminal, so this unique-indexed probe IS "the one live assignment, if any".
     */
    Optional<DeviceAssignmentEntity> findByDeviceActiveGuard(String deviceRef);

    List<DeviceAssignmentEntity> findByTenantIdAndPlanIdOrderByCreatedAtDesc(UUID tenantId, UUID planId);

    List<DeviceAssignmentEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    List<DeviceAssignmentEntity> findByDeviceRefOrderByCreatedAtDesc(String deviceRef);
}
