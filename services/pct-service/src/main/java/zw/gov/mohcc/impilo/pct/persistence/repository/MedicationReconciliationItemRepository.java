package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MedicationReconciliationItemRepository extends JpaRepository<MedicationReconciliationItemEntity, UUID> {

    List<MedicationReconciliationItemEntity> findByReconciliationId(UUID reconciliationId);

    /**
     * Medicine count per COMPLETED reconciliation in the window — the polypharmacy distribution.
     *
     * <p>Only completed reconciliations count. An abandoned or in-progress reconciliation has an item
     * list that stops wherever the clinician stopped, and reading it as a medicine count makes an
     * interrupted review look like a patient on fewer drugs.</p>
     *
     * <p>A reconciliation with zero items produces no row here at all. That is not a defect to work
     * around in SQL but a fact the caller must be told: the service compares this row count against
     * {@link MedicationReconciliationRepository#countCompleted} and reports the difference, because
     * silently dropping the empty ones shrinks the denominator and inflates the polypharmacy rate.</p>
     */
    @Query("""
           SELECT i.reconciliationId, COUNT(i)
             FROM MedicationReconciliationItemEntity i, MedicationReconciliationEntity r
            WHERE r.tenantId = :tenantId
              AND i.reconciliationId = r.reconciliationId
              AND r.status = 'COMPLETED'
              AND r.completedAt >= :from
              AND r.completedAt < :until
            GROUP BY i.reconciliationId
           """)
    List<Object[]> itemCountsPerCompletedReconciliation(@Param("tenantId") UUID tenantId,
                                                        @Param("from") OffsetDateTime from,
                                                        @Param("until") OffsetDateTime until);
}
