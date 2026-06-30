package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;

import java.util.List;
import java.util.UUID;

/** Repository for external sync state. */
@Repository
public interface ExternalSyncStateRepository extends JpaRepository<ExternalSyncStateEntity, UUID> {

    List<ExternalSyncStateEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, SyncStatus status);

    List<ExternalSyncStateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
