package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetItemEntity;

public interface EmergencyOrderSetItemRepository extends JpaRepository<EmergencyOrderSetItemEntity, UUID> {
    Optional<EmergencyOrderSetItemEntity> findByItemIdAndTenantId(UUID itemId, UUID tenantId);
    List<EmergencyOrderSetItemEntity> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId);
}
