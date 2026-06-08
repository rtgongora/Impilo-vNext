package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodStockReconciliationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodStockReconciliationRepository extends JpaRepository<BloodStockReconciliationEntity, Long> {
    Optional<BloodStockReconciliationEntity> findByReconciliationIdAndTenantId(UUID reconciliationId, UUID tenantId);
    List<BloodStockReconciliationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
