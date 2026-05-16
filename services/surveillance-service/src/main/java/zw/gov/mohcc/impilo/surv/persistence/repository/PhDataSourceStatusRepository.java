package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.PhDataSourceStatusEntity;

public interface PhDataSourceStatusRepository extends JpaRepository<PhDataSourceStatusEntity, Long> {
    List<PhDataSourceStatusEntity> findByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    Optional<PhDataSourceStatusEntity> findByTenantIdAndSourceKey(UUID tenantId, String sourceKey);
    long countByTenantIdAndLastSyncAtBefore(UUID tenantId, OffsetDateTime staleBefore);
}
