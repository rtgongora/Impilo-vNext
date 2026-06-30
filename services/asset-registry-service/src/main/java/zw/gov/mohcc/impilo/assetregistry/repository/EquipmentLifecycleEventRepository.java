package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.EquipmentLifecycleEventEntity;

import java.util.List;
import java.util.UUID;

public interface EquipmentLifecycleEventRepository extends JpaRepository<EquipmentLifecycleEventEntity, UUID> {
    List<EquipmentLifecycleEventEntity> findByEquipmentIdOrderByOccurredAtDesc(UUID equipmentId);
    long countByEquipmentIdAndEventType(UUID equipmentId, String eventType);
}
