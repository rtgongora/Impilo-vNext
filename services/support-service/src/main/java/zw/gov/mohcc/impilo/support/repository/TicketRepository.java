package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.support.domain.TicketEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {
    @Query("SELECT t FROM TicketEntity t WHERE t.tenantId = :tenantId"
            + " AND (:status IS NULL OR t.status = :status)"
            + " AND (:priority IS NULL OR t.priority = :priority)"
            + " AND (:category IS NULL OR t.category = :category)"
            + " AND (:assigneeRef IS NULL OR t.assigneeRef = :assigneeRef)")
    Page<TicketEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                     @Param("status") String status,
                                     @Param("priority") String priority,
                                     @Param("category") String category,
                                     @Param("assigneeRef") String assigneeRef,
                                     Pageable pageable);

    @Query("SELECT t FROM TicketEntity t WHERE t.createdAt <= :asOf")
    Page<TicketEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndPriority(UUID tenantId, String priority);

    long countByTenantIdAndEscalationLevelGreaterThan(UUID tenantId, int level);

    /**
     * Native SQL: JPQL does not support {@code EXTRACT(EPOCH FROM ...)} on datetime differences.
     * Matches PostgreSQL semantics; H2 test DB uses {@code MODE=PostgreSQL}.
     */
    @Query(value = "SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0), 0) "
            + "FROM sup_tickets t WHERE t.tenant_id = :tenantId AND t.resolved_at IS NOT NULL",
            nativeQuery = true)
    double avgResolutionHours(@Param("tenantId") UUID tenantId);
}
