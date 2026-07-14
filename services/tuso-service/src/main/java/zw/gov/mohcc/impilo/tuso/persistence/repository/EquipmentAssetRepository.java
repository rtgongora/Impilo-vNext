package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EquipmentAssetEntity;

import java.util.List;
import java.util.UUID;

public interface EquipmentAssetRepository extends JpaRepository<EquipmentAssetEntity, UUID> {

    List<EquipmentAssetEntity> findByTenantIdAndMaintenanceStateOrderByNameAsc(UUID tenantId, String maintenanceState);

    List<EquipmentAssetEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<EquipmentAssetEntity> findByTenantIdAndFacilityIdOrderByNameAsc(UUID tenantId, Long facilityId);
}
