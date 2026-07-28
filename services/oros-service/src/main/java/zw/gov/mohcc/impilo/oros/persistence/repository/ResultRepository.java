package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {

    List<ResultEntity> findByOrderIdOrderByCreatedAtDesc(String orderId);

    /** Batch form — see OrderItemRepository.findByOrderIdIn. */
    List<ResultEntity> findByOrderIdIn(Collection<String> orderIds);

    /** Highest-version result of a given report status for an order (e.g. the current FINAL). */
    Optional<ResultEntity> findFirstByOrderIdAndReportStatusOrderByVersionDesc(
            String orderId, ResultStatus reportStatus);

    /** Highest-version result for an order, regardless of status (head of the version chain). */
    Optional<ResultEntity> findFirstByOrderIdOrderByVersionDesc(String orderId);

    /** Full version chain for an order, newest version first (reporting history view). */
    List<ResultEntity> findByOrderIdOrderByVersionDescCreatedAtDesc(String orderId);

    /**
     * Critical results in the tenant that have not yet been acknowledged (joined to their order for
     * tenant scoping, since results carry no tenant column).
     */
    @Query("""
            SELECT r FROM ResultEntity r, OrderEntity o
            WHERE o.orderId = r.orderId
              AND o.tenantId = :tenantId
              AND r.isCritical = true
              AND r.acknowledgedAt IS NULL
            ORDER BY r.createdAt ASC
            """)
    List<ResultEntity> criticalUnacknowledged(@Param("tenantId") UUID tenantId);

    /**
     * Critical results across all tenants that remain unacknowledged and un-escalated past the ack
     * threshold — drives the escalation scheduler (which marks {@code escalatedAt} to fire once).
     */
    List<ResultEntity> findByIsCriticalTrueAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndCreatedAtBefore(
            java.time.OffsetDateTime threshold);
}
