package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link QueueEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface QueueRepository extends JpaRepository<QueueEntity, UUID> {

    /**
     * Finds all queues at a given facility within a tenant.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @return list of queues at the facility
     */
    List<QueueEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    /**
     * Finds active queues for a specific workspace within a tenant.
     *
     * @param tenantId    the tenant identifier
     * @param workspaceId the workspace identifier
     * @return list of active queues at the workspace
     */
    List<QueueEntity> findByTenantIdAndWorkspaceIdAndActiveTrue(UUID tenantId, UUID workspaceId);

    /**
     * Finds a specific queue by tenant and queue ID.
     *
     * @param tenantId the tenant identifier
     * @param queueId  the queue identifier
     * @return the queue if found
     */
    Optional<QueueEntity> findByTenantIdAndQueueId(UUID tenantId, UUID queueId);

    /** All queues at a facility with a given source — used by materialisation to reconcile + retire. */
    List<QueueEntity> findByTenantIdAndFacilityIdAndSource(UUID tenantId, UUID facilityId, String source);

    /** The materialised queue for a given TUSO source reference — the idempotent upsert key. */
    Optional<QueueEntity> findByTenantIdAndFacilityIdAndSourceRef(UUID tenantId, UUID facilityId, String sourceRef);

    // ---- virtual-pool queues (V034) ------------------------------------

    /** The materialised virtual-pool queue for a TUSO sourceRef ('{vsUid}:{queueKey}') — idempotent upsert key. */
    Optional<QueueEntity> findByTenantIdAndSourceRefAndVirtualPoolIdIsNotNull(UUID tenantId, String sourceRef);

    /** All TUSO-materialised virtual-pool queues for a tenant — reconcile + retire scope. */
    List<QueueEntity> findByTenantIdAndSourceAndVirtualPoolIdIsNotNull(UUID tenantId, String source);

    /** Active materialised queues for a pool key (routing seam target_ref), stable order. */
    List<QueueEntity> findByTenantIdAndVirtualPoolIdAndActiveTrueOrderBySourceRefAsc(UUID tenantId, String virtualPoolId);

    /** Every queue for a pool key (incl. retired) — status/read-back surface. */
    List<QueueEntity> findByTenantIdAndVirtualPoolIdOrderBySourceRefAsc(UUID tenantId, String virtualPoolId);
}
