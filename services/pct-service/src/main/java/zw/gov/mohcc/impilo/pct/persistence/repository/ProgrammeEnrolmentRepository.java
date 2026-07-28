package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgrammeEnrolmentRepository extends JpaRepository<ProgrammeEnrolmentEntity, UUID> {

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid);

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidAndStatusInOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid, Collection<String> statuses);

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidAndProgrammeOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid, String programme);

    /** The one active enrolment for a programme, if any — the partial-unique constraint guarantees ≤1. */
    Optional<ProgrammeEnrolmentEntity> findFirstByTenantIdAndSubjectCpidAndProgrammeAndStatusNot(
            UUID tenantId, String subjectCpid, String programme, String status);

    Optional<ProgrammeEnrolmentEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);

    /**
     * Programme cohort counts, grouped by programme and status.
     *
     * <p>Deliberately an aggregate in the system of record rather than a report definition executing
     * SQL elsewhere: reporting-service holds a separate database and cannot see {@code pct.*} at all,
     * so a seeded template naming these tables would be registered, ACTIVE and unrunnable.</p>
     *
     * <p>Counts enrolments, not people — a person on both HIV care and TB treatment is in both
     * cohorts, which is the clinically correct reading for programme reporting and is stated on the
     * response so nobody totals the rows into a patient count.</p>
     */
    @Query("""
           SELECT e.programme, e.status, COUNT(e)
             FROM ProgrammeEnrolmentEntity e
            WHERE e.tenantId = :tenantId
              AND (:facilityId IS NULL OR e.managingFacilityId = :facilityId)
            GROUP BY e.programme, e.status
           """)
    List<Object[]> cohortCounts(@Param("tenantId") UUID tenantId,
                               @Param("facilityId") String facilityId);
}
