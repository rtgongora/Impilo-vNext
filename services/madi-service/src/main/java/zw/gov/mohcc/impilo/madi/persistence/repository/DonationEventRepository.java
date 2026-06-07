package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonationEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationEventRepository extends JpaRepository<DonationEventEntity, Long> {
    Optional<DonationEventEntity> findByEventIdAndTenantId(UUID eventId, UUID tenantId);
    List<DonationEventEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
