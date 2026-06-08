package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodUnitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodUnitRepository extends JpaRepository<BloodUnitEntity, Long> {
    Optional<BloodUnitEntity> findByUnitIdAndTenantId(UUID unitId, UUID tenantId);
    List<BloodUnitEntity> findByTenantIdAndStatusAndBloodGroupAndComponentType(
            UUID tenantId, String status, String bloodGroup, String componentType);
    List<BloodUnitEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
