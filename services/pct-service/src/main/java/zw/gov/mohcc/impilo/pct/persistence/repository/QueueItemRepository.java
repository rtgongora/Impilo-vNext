package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.QueueItemEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link QueueItemEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 * Queue items are ordered by priority descending (highest first), then by creation time ascending (oldest first).
 */
@Repository
public interface QueueItemRepository extends JpaRepository<QueueItemEntity, UUID> {

    // ------------------------------------------------------------------
    // Tenant-scoped methods
    // ------------------------------------------------------------------

    /**
     * Finds all items in a queue with a specific status, ordered by priority (desc) then creation time (asc).
     */
    List<QueueItemEntity> findByTenantIdAndQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
            UUID tenantId, UUID queueId, QueueItemStatus status);

    /**
     * Finds all items in a queue matching any of the given statuses, ordered by priority (desc) then creation time (asc).
     */
    List<QueueItemEntity> findByTenantIdAndQueueIdAndStatusInOrderByPriorityDescCreatedAtAsc(
            UUID tenantId, UUID queueId, List<QueueItemStatus> statuses);

    /**
     * Finds a specific queue item by tenant and item ID.
     */
    Optional<QueueItemEntity> findByTenantIdAndItemId(UUID tenantId, UUID itemId);

    /**
     * Finds all queue items associated with a journey within a tenant.
     */
    List<QueueItemEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Counts items in a queue with a given status (tenant-scoped).
     */
    long countByTenantIdAndQueueIdAndStatus(UUID tenantId, UUID queueId, QueueItemStatus status);

    /**
     * Finds the next item to be served in a queue (highest priority, oldest first) — tenant-scoped.
     */
    Optional<QueueItemEntity> findFirstByTenantIdAndQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
            UUID tenantId, UUID queueId, QueueItemStatus status);

    /**
     * Finds the item with the highest token number in a queue — tenant-scoped.
     */
    Optional<QueueItemEntity> findTopByTenantIdAndQueueIdOrderByTokenNumberDesc(UUID tenantId, UUID queueId);

    // ------------------------------------------------------------------
    // Non-tenant-scoped methods (used by QueueEngine & ControlTowerService)
    // ------------------------------------------------------------------

    /**
     * Finds the next item to be served in a queue (highest priority, oldest first).
     */
    Optional<QueueItemEntity> findFirstByQueueIdAndStatusOrderByPriorityDescCreatedAtAsc(
            UUID queueId, QueueItemStatus status);

    /**
     * Counts items in a queue with a given status.
     */
    long countByQueueIdAndStatus(UUID queueId, QueueItemStatus status);

    /**
     * Finds all items in a queue with a specific status.
     */
    List<QueueItemEntity> findByQueueIdAndStatus(UUID queueId, QueueItemStatus status);

    /**
     * Finds the item with the highest token number in a queue.
     */
    Optional<QueueItemEntity> findTopByQueueIdOrderByTokenNumberDesc(UUID queueId);

    /**
     * Returns the maximum token number currently assigned in a queue, or 0 if no items exist.
     */
    @Query("SELECT COALESCE(MAX(q.tokenNumber), 0) FROM QueueItemEntity q WHERE q.queueId = :queueId")
    int findMaxTokenNumberByQueueId(@Param("queueId") UUID queueId);

    /**
     * Discovers distinct queue IDs that have items with any of the given statuses at a facility.
     * Used by the control tower to find active queues.
     */
    @Query("SELECT DISTINCT q.queueId FROM QueueItemEntity q WHERE q.facilityId = :facilityId AND q.status IN :statuses")
    List<UUID> findDistinctQueueIdsByFacilityIdAndStatusIn(
            @Param("facilityId") UUID facilityId,
            @Param("statuses") Collection<QueueItemStatus> statuses);
}
