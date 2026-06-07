package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationDriveEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationDriveRepository extends JpaRepository<DonationDriveEntity, Long> {
    Optional<DonationDriveEntity> findByDriveIdAndTenantId(UUID driveId, UUID tenantId);
    List<DonationDriveEntity> findByTenantIdAndStatusOrderByStartAtAsc(UUID tenantId, String status);
    List<DonationDriveEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
