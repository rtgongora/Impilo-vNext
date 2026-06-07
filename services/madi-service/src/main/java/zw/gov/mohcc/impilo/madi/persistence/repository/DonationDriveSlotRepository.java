package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationDriveSlotEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationDriveSlotRepository extends JpaRepository<DonationDriveSlotEntity, Long> {
    Optional<DonationDriveSlotEntity> findBySlotIdAndTenantId(UUID slotId, UUID tenantId);
    List<DonationDriveSlotEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
