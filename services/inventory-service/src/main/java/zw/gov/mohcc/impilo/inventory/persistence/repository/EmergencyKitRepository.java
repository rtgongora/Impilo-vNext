package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EmergencyKitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyKitRepository extends JpaRepository<EmergencyKitEntity, UUID> {
    List<EmergencyKitEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
    Optional<EmergencyKitEntity> findByTenantIdAndKitId(UUID tenantId, UUID kitId);
}
