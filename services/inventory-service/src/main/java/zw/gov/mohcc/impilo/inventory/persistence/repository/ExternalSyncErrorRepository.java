package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncErrorEntity;

import java.util.List;
import java.util.UUID;

/** Repository for external sync error history. */
@Repository
public interface ExternalSyncErrorRepository extends JpaRepository<ExternalSyncErrorEntity, UUID> {

    List<ExternalSyncErrorEntity> findBySyncIdOrderByCreatedAtAsc(UUID syncId);
}
