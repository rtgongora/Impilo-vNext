package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodFridgeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodFridgeRepository extends JpaRepository<BloodFridgeEntity, Long> {
    Optional<BloodFridgeEntity> findByFridgeIdAndTenantId(UUID fridgeId, UUID tenantId);
    List<BloodFridgeEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<BloodFridgeEntity> findByTenantIdAndBloodBankIdOrderByCreatedAtDesc(UUID tenantId, UUID bloodBankId);
}
