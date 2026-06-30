package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.EquipmentTransferEntity;

import java.util.List;
import java.util.UUID;

public interface EquipmentTransferRepository extends JpaRepository<EquipmentTransferEntity, UUID> {
    List<EquipmentTransferEntity> findByEquipmentIdOrderByInitiatedAtDesc(UUID equipmentId);
    List<EquipmentTransferEntity> findByTenantIdAndStatusOrderByInitiatedAtDesc(UUID tenantId, String status);
}
