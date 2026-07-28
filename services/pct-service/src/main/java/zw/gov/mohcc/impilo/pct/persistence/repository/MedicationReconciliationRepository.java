package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MedicationReconciliationRepository extends JpaRepository<MedicationReconciliationEntity, UUID> {

    List<MedicationReconciliationEntity> findByTenantIdAndSubjectCpidOrderByStartedAtDesc(UUID tenantId, String subjectCpid);

    /** Completed reconciliations in the window — the polypharmacy denominator, empty ones included. */
    @Query("""
           SELECT COUNT(r)
             FROM MedicationReconciliationEntity r
            WHERE r.tenantId = :tenantId
              AND r.status = 'COMPLETED'
              AND r.completedAt >= :from
              AND r.completedAt < :until
           """)
    long countCompleted(@Param("tenantId") UUID tenantId,
                        @Param("from") OffsetDateTime from,
                        @Param("until") OffsetDateTime until);

    /** People behind those reconciliations — one person reconciled at admission and discharge is one. */
    @Query("""
           SELECT COUNT(DISTINCT r.subjectCpid)
             FROM MedicationReconciliationEntity r
            WHERE r.tenantId = :tenantId
              AND r.status = 'COMPLETED'
              AND r.completedAt >= :from
              AND r.completedAt < :until
           """)
    long countCompletedDistinctSubjects(@Param("tenantId") UUID tenantId,
                                        @Param("from") OffsetDateTime from,
                                        @Param("until") OffsetDateTime until);
}
