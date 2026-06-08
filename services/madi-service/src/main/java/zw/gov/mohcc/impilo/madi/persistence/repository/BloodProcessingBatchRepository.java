package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodProcessingBatchEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodProcessingBatchRepository extends JpaRepository<BloodProcessingBatchEntity, Long> {
    Optional<BloodProcessingBatchEntity> findByBatchIdAndTenantId(UUID batchId, UUID tenantId);
    List<BloodProcessingBatchEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
