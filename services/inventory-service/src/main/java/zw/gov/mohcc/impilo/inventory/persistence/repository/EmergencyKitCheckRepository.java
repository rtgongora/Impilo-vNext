package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitCheckEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmergencyKitCheckRepository extends JpaRepository<EmergencyKitCheckEntity, UUID> {
    List<EmergencyKitCheckEntity> findByTenantIdAndKitIdOrderByCheckedAtDesc(UUID tenantId, UUID kitId);
    EmergencyKitCheckEntity findFirstByTenantIdAndKitIdOrderByCheckedAtDesc(UUID tenantId, UUID kitId);
}
