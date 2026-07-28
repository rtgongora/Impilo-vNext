package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<ProblemEntity, UUID> {

    List<ProblemEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);

    List<ProblemEntity> findByTenantIdAndSubjectCpidAndClinicalStatusOrderByCreatedAtDesc(
            UUID tenantId, String subjectCpid, String clinicalStatus);

    // ── §21 analytics aggregates ────────────────────────────────────────────────
    //
    // These are executed by PCT itself, not by reporting-service. reporting holds a SEPARATE
    // DATABASE with no view of pct.* — no FDW, no dblink, no ETL — so a seeded SQL report
    // definition naming these tables would be registered, ACTIVE and permanently unrunnable.
    // Five governed report definitions in this estate are already in exactly that state.

    /**
     * Disease detection: problems first recorded in the window, by code and diagnostic certainty.
     *
     * <p>Counts problem ROWS, not people, and the window is on {@code created_at} (when it was
     * recorded here) rather than {@code onset_date} (when the patient says it began). Those are
     * different questions and the response says which one this answers.</p>
     *
     * <p>Certainty is part of the grouping rather than a filter, because a SUSPECTED and a CONFIRMED
     * diagnosis of the same code are not the same detection and collapsing them overstates incidence.
     * A null certainty is a legitimate recorded state and appears as its own group.</p>
     */
    @Query("""
           SELECT p.code, p.display, p.diagnosticCertainty, COUNT(p)
             FROM ProblemEntity p
            WHERE p.tenantId = :tenantId
              AND p.category = :category
              AND p.createdAt >= :from
              AND p.createdAt < :until
            GROUP BY p.code, p.display, p.diagnosticCertainty
            ORDER BY COUNT(p) DESC
           """)
    List<Object[]> detectionCounts(@Param("tenantId") UUID tenantId,
                                   @Param("category") String category,
                                   @Param("from") OffsetDateTime from,
                                   @Param("until") OffsetDateTime until);

    /** The people behind {@link #detectionCounts} — so the row count is never read as a headcount. */
    @Query("""
           SELECT COUNT(DISTINCT p.subjectCpid)
             FROM ProblemEntity p
            WHERE p.tenantId = :tenantId
              AND p.category = :category
              AND p.createdAt >= :from
              AND p.createdAt < :until
           """)
    long detectionDistinctSubjects(@Param("tenantId") UUID tenantId,
                                   @Param("category") String category,
                                   @Param("from") OffsetDateTime from,
                                   @Param("until") OffsetDateTime until);

    /**
     * Diagnostic delay: stated onset and the moment the confirmed diagnosis was recorded.
     *
     * <p>Returns the two dates rather than an interval because the arithmetic differs between
     * Postgres and the H2 the tests run on, and a dialect-specific aggregate is a query that passes
     * in test and fails in production. The interval is computed in Java over a bounded window.</p>
     *
     * <p>Only CONFIRMED diagnoses that carry an onset date can appear. The ones without an onset are
     * counted separately by {@link #confirmedDiagnosesWithoutOnset} and reported alongside, because a
     * delay statistic computed over only the records that happen to have both dates is a survivorship
     * claim about the records, not a measurement of the service.</p>
     */
    @Query("""
           SELECT p.onsetDate, p.createdAt
             FROM ProblemEntity p
            WHERE p.tenantId = :tenantId
              AND p.category = 'DIAGNOSIS'
              AND p.diagnosticCertainty = 'CONFIRMED'
              AND p.onsetDate IS NOT NULL
              AND p.createdAt >= :from
              AND p.createdAt < :until
           """)
    List<Object[]> confirmedDiagnosisOnsetAndRecord(@Param("tenantId") UUID tenantId,
                                                    @Param("from") OffsetDateTime from,
                                                    @Param("until") OffsetDateTime until);

    /** Confirmed diagnoses in the window with no onset date — the excluded denominator. */
    @Query("""
           SELECT COUNT(p)
             FROM ProblemEntity p
            WHERE p.tenantId = :tenantId
              AND p.category = 'DIAGNOSIS'
              AND p.diagnosticCertainty = 'CONFIRMED'
              AND p.onsetDate IS NULL
              AND p.createdAt >= :from
              AND p.createdAt < :until
           """)
    long confirmedDiagnosesWithoutOnset(@Param("tenantId") UUID tenantId,
                                        @Param("from") OffsetDateTime from,
                                        @Param("until") OffsetDateTime until);

    /**
     * Review state of the open problem list as at a date: overdue, due today, scheduled, or never set.
     *
     * <p>NO_REVIEW_DATE is its own bucket and is never merged into SCHEDULED. A problem nobody set a
     * review date for is not a problem that is up to date — it is a problem with no follow-up
     * intention recorded at all, and folding it into the compliant group is how a follow-up indicator
     * comes out green on a service that is not following anybody up.</p>
     */
    @Query("""
           SELECT CASE WHEN p.reviewDate IS NULL THEN 'NO_REVIEW_DATE'
                       WHEN p.reviewDate < :asOf THEN 'OVERDUE'
                       WHEN p.reviewDate = :asOf THEN 'DUE_TODAY'
                       ELSE 'SCHEDULED' END,
                  COUNT(p)
             FROM ProblemEntity p
            WHERE p.tenantId = :tenantId
              AND p.clinicalStatus IN :openStatuses
            GROUP BY CASE WHEN p.reviewDate IS NULL THEN 'NO_REVIEW_DATE'
                          WHEN p.reviewDate < :asOf THEN 'OVERDUE'
                          WHEN p.reviewDate = :asOf THEN 'DUE_TODAY'
                          ELSE 'SCHEDULED' END
           """)
    List<Object[]> reviewStateCounts(@Param("tenantId") UUID tenantId,
                                     @Param("openStatuses") Collection<String> openStatuses,
                                     @Param("asOf") LocalDate asOf);
}
