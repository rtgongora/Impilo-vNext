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

    /**
     * The register itself — who is on it, at one facility, with how their condition is running.
     *
     * <p><strong>This is the first cohort read in the estate.</strong> Every other problem and
     * enrolment query here is subject-scoped: they answer "what does this patient have". A register
     * is the other question — "who at this facility is on this register, and who among them needs
     * recalling" — and until V112 added {@code idx_pct_programme_enrolments_cohort} no index led with
     * the facility, so there was no efficient way to ask it. That access path, not a table, was what
     * chronic-disease registers actually cost.</p>
     *
     * <p>Ordered by control status ascending with NULLs first, then by assessment date, so the two
     * groups a recall list exists to find come out at the top: the people nobody has assessed, and
     * then the people assessed longest ago. A register sorted by name is an administrative document;
     * sorted this way it is a worklist.</p>
     *
     * <p>Excludes EXITED by default via the status filter the caller supplies. Left to the caller
     * rather than hardcoded, because "who left this register and why" is a real clinical question —
     * particularly for the people who left as LOST_TO_FOLLOW_UP.</p>
     */
    @Query("""
           SELECT e
             FROM ProgrammeEnrolmentEntity e
            WHERE e.tenantId = :tenantId
              AND e.programme = :programme
              AND (:facilityId IS NULL OR e.managingFacilityId = :facilityId)
              AND (:statuses IS NULL OR e.status IN :statuses)
            ORDER BY CASE WHEN e.controlStatus IS NULL THEN 0 ELSE 1 END,
                     e.controlAssessedOn ASC NULLS FIRST,
                     e.enrolledOn ASC
           """)
    List<ProgrammeEnrolmentEntity> register(@Param("tenantId") UUID tenantId,
                                            @Param("programme") String programme,
                                            @Param("facilityId") String facilityId,
                                            @Param("statuses") Collection<String> statuses);
}
