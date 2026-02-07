package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ReconcileQueueEntity;

import java.util.UUID;

@Repository
public interface ReconcileQueueRepository extends JpaRepository<ReconcileQueueEntity, UUID> {

    Page<ReconcileQueueEntity> findByTenantIdAndStatus(UUID tenantId, ReconcileStatus status, Pageable pageable);

    Page<ReconcileQueueEntity> findByFacilityIdAndStatus(UUID facilityId, ReconcileStatus status, Pageable pageable);
}
