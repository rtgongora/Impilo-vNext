package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodStockMovementEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodStockMovementRepository extends JpaRepository<BloodStockMovementEntity, Long> {
    Optional<BloodStockMovementEntity> findByMovementIdAndTenantId(UUID movementId, UUID tenantId);
    List<BloodStockMovementEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
