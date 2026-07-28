package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.TreatmentRegimenEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRegimenRepository extends JpaRepository<TreatmentRegimenEntity, UUID> {

    List<TreatmentRegimenEntity> findByTenantIdAndEnrolmentIdOrderByStartedOnDesc(
            UUID tenantId, UUID enrolmentId);

    /** The current regimen for an enrolment — the partial-unique constraint guarantees ≤1 open row. */
    Optional<TreatmentRegimenEntity> findByTenantIdAndEnrolmentIdAndEndedOnIsNull(
            UUID tenantId, UUID enrolmentId);

    /**
     * Why regimens ended in the window, by programme — STOCK_OUT among the reasons.
     *
     * <p>This is the only stock signal that exists inside the clinical record: it counts the times a
     * shortage was severe enough to change a patient's treatment. It is <strong>not</strong> a
     * stockout rate — it cannot see a stockout that was absorbed, substituted at the pharmacy counter,
     * or that simply sent the patient home empty-handed, and it has no denominator of facility-days.
     * The service labels it accordingly so it is never read as inventory availability.</p>
     */
    @Query("""
           SELECT e.programme, r.changeReason, COUNT(r)
             FROM TreatmentRegimenEntity r, ProgrammeEnrolmentEntity e
            WHERE r.tenantId = :tenantId
              AND r.enrolmentId = e.enrolmentId
              AND (:programme IS NULL OR e.programme = :programme)
              AND r.endedOn IS NOT NULL
              AND r.endedOn >= :from
              AND r.endedOn <= :to
            GROUP BY e.programme, r.changeReason
           """)
    List<Object[]> changeReasonCounts(@Param("tenantId") UUID tenantId,
                                      @Param("programme") String programme,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
