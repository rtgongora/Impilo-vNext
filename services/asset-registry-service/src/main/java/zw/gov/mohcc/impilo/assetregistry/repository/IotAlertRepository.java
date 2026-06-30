package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.IotAlertEntity;

import java.util.List;
import java.util.UUID;

public interface IotAlertRepository extends JpaRepository<IotAlertEntity, UUID> {
    List<IotAlertEntity> findByEquipmentIdOrderByObservedAtDesc(UUID equipmentId);
    List<IotAlertEntity> findByTenantIdAndStatusOrderByObservedAtDesc(UUID tenantId, String status);
    List<IotAlertEntity> findByDeviceIdAndAlertTypeAndStatus(String deviceId, String alertType, String status);
}
