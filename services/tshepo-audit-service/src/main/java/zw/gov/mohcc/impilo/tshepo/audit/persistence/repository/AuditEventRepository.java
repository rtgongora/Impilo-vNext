package zw.gov.mohcc.impilo.tshepo.audit.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID>,
        JpaSpecificationExecutor<AuditEventEntity> {

    /**
     * Find audit events by tenant and subject reference (patient access history).
     */
    Page<AuditEventEntity> findByTenantIdAndSubjectRef(UUID tenantId, String subjectRef, Pageable pageable);

    /**
     * Find audit events by tenant within a sequence number range (for export).
     */
    @Query("SELECT e FROM AuditEventEntity e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.sequenceNumber >= :fromSequence " +
           "AND e.sequenceNumber <= :toSequence " +
           "ORDER BY e.sequenceNumber ASC")
    List<AuditEventEntity> findByTenantIdAndSequenceNumberBetween(
            @Param("tenantId") UUID tenantId,
            @Param("fromSequence") long fromSequence,
            @Param("toSequence") long toSequence);

    /**
     * Find audit events by tenant and actor (who did what).
     */
    Page<AuditEventEntity> findByTenantIdAndActorId(UUID tenantId, String actorId, Pageable pageable);

    /**
     * Find audit events by tenant and event type.
     */
    Page<AuditEventEntity> findByTenantIdAndEventType(UUID tenantId, String eventType, Pageable pageable);

    /**
     * Find audit events by tenant within a time range.
     */
    Page<AuditEventEntity> findByTenantIdAndCreatedAtBetween(
            UUID tenantId, Instant from, Instant to, Pageable pageable);

    /**
     * Find all audit events for a tenant ordered by sequence number descending.
     */
    Page<AuditEventEntity> findByTenantIdOrderBySequenceNumberDesc(UUID tenantId, Pageable pageable);

    /**
     * Find event by tenant and sequence number (for chain verification).
     */
    Optional<AuditEventEntity> findByTenantIdAndSequenceNumber(UUID tenantId, Long sequenceNumber);

    /**
     * Find the latest event for a tenant (highest sequence number).
     */
    Optional<AuditEventEntity> findFirstByTenantIdOrderBySequenceNumberDesc(UUID tenantId);

    /**
     * Count events for a tenant within a sequence range.
     */
    @Query("SELECT COUNT(e) FROM AuditEventEntity e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.sequenceNumber >= :fromSequence " +
           "AND e.sequenceNumber <= :toSequence")
    long countByTenantIdAndSequenceRange(
            @Param("tenantId") UUID tenantId,
            @Param("fromSequence") long fromSequence,
            @Param("toSequence") long toSequence);
}
