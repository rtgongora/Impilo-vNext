package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.MaintenanceTaskEntity;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTaskEntity, UUID> {
    List<MaintenanceTaskEntity> findByEquipmentIdOrderByOpenedAtDesc(UUID equipmentId);
    List<MaintenanceTaskEntity> findByTenantIdAndStatusOrderByOpenedAtDesc(UUID tenantId, String status);
    List<MaintenanceTaskEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);
}
