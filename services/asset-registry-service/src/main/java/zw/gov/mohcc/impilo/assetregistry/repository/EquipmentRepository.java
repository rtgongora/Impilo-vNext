package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.assetregistry.domain.EquipmentEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, UUID> {

    Page<EquipmentEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId, Pageable pageable);

    Page<EquipmentEntity> findByTenantIdAndFacilityIdOrderByNameAsc(UUID tenantId, String facilityId, Pageable pageable);

    List<EquipmentEntity> findByTenantIdAndEquipmentType(UUID tenantId, String equipmentType);

    List<EquipmentEntity> findByAssetId(UUID assetId);

    @Query("SELECT e FROM EquipmentEntity e WHERE e.tenantId = :tenantId AND e.nextMaintenance <= :cutoff ORDER BY e.nextMaintenance ASC")
    List<EquipmentEntity> findMaintenanceDue(@Param("tenantId") UUID tenantId, @Param("cutoff") LocalDate cutoff);

    @Query("SELECT e FROM EquipmentEntity e WHERE e.tenantId = :tenantId AND e.nextCalibration <= :cutoff ORDER BY e.nextCalibration ASC")
    List<EquipmentEntity> findCalibrationDue(@Param("tenantId") UUID tenantId, @Param("cutoff") LocalDate cutoff);

    List<EquipmentEntity> findByDeviceId(String deviceId);

    List<EquipmentEntity> findByTenantIdAndSerialNumber(UUID tenantId, String serialNumber);

    List<EquipmentEntity> findByTenantIdAndTag(UUID tenantId, String tag);

    List<EquipmentEntity> findByTenantIdAndFacilityIdAndEquipmentType(UUID tenantId, String facilityId, String equipmentType);
}
