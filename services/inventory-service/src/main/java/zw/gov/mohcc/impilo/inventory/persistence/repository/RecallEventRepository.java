package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Dura recall events.
 */
@Repository
public interface RecallEventRepository extends JpaRepository<RecallEventEntity, UUID> {

    List<RecallEventEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<RecallEventEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, RecallStatus status);
}
