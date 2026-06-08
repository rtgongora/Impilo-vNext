package zw.gov.mohcc.impilo.iotingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.iotingestion.domain.DeviceRegistryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRegistryRepository extends JpaRepository<DeviceRegistryEntity, UUID> {
    List<DeviceRegistryEntity> findByTenantIdAndStatusNotOrderByRegisteredAtDesc(UUID tenantId, String status);
    List<DeviceRegistryEntity> findByTenantIdAndOwnerHealthIdAndStatusNotOrderByRegisteredAtDesc(
            UUID tenantId, String ownerHealthId, String status);
    Optional<DeviceRegistryEntity> findByTenantIdAndExternalDeviceId(UUID tenantId, String externalDeviceId);
    Optional<DeviceRegistryEntity> findByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);
}
