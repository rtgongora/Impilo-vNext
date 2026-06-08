package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationDriveSyncConflictEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationDriveSyncConflictRepository extends JpaRepository<DonationDriveSyncConflictEntity, Long> {
    Optional<DonationDriveSyncConflictEntity> findByConflictIdAndTenantId(UUID conflictId, UUID tenantId);
    Optional<DonationDriveSyncConflictEntity> findByConflictIdAndDriveIdAndTenantId(
            UUID conflictId, UUID driveId, UUID tenantId);
}
