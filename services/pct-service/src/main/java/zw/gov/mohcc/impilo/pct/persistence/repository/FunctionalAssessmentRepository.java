package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.FunctionalAssessmentEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FunctionalAssessmentRepository extends JpaRepository<FunctionalAssessmentEntity, UUID> {

    List<FunctionalAssessmentEntity> findByTenantIdAndSubjectCpidOrderByAssessmentDateDesc(UUID tenantId, String subjectCpid);

    /**
     * Functional assessments in the window by instrument, split on whether a score was recorded.
     *
     * <p>The split matters because the write path allows a score or an absent reason and never both:
     * an assessment that could not be completed is a real record with no number in it, and averaging
     * over the ones that do have numbers would describe only the patients well enough to be
     * assessed.</p>
     */
    @Query("""
           SELECT f.assessmentType,
                  CASE WHEN f.totalScore IS NULL THEN 'SCORE_ABSENT' ELSE 'SCORE_PRESENT' END,
                  COUNT(f)
             FROM FunctionalAssessmentEntity f
            WHERE f.tenantId = :tenantId
              AND f.assessmentDate >= :from
              AND f.assessmentDate <= :to
            GROUP BY f.assessmentType,
                     CASE WHEN f.totalScore IS NULL THEN 'SCORE_ABSENT' ELSE 'SCORE_PRESENT' END
           """)
    List<Object[]> assessmentCounts(@Param("tenantId") UUID tenantId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /**
     * One row per person who has more than one assessment on the same instrument in the window.
     *
     * <p>This is the denominator a functional <em>outcome</em> would need — you cannot measure change
     * from a single measurement — and it deliberately selects the instrument and the count rather
     * than the CPID, so a service-level indicator never carries patient identifiers.</p>
     */
    @Query("""
           SELECT f.assessmentType, COUNT(f)
             FROM FunctionalAssessmentEntity f
            WHERE f.tenantId = :tenantId
              AND f.assessmentDate >= :from
              AND f.assessmentDate <= :to
            GROUP BY f.assessmentType, f.subjectCpid
           HAVING COUNT(f) > 1
           """)
    List<Object[]> repeatMeasureSubjects(@Param("tenantId") UUID tenantId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
