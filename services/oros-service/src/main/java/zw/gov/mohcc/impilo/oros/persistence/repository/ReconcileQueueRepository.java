package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ReconcileQueueEntity;

import java.util.UUID;

@Repository
public interface ReconcileQueueRepository extends JpaRepository<ReconcileQueueEntity, UUID> {

    Page<ReconcileQueueEntity> findByTenantIdAndStatus(
            UUID tenantId, ReconcileStatus status, Pageable pageable);
}
