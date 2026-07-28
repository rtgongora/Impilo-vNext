package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ProblemLinkRepository extends JpaRepository<ProblemLinkEntity, UUID> {

    List<ProblemLinkEntity> findByTenantIdAndProblemIdOrderByCreatedAtDesc(UUID tenantId, UUID problemId);

    /** Reverse lookup: what does this observation support, what is this medicine treating. */
    List<ProblemLinkEntity> findByTenantIdAndTargetTypeAndTargetRef(
            UUID tenantId, String targetType, String targetRef);

    /**
     * Complications: which complication was recorded against which underlying condition.
     *
     * <p>A COMPLICATION_OF link is the only place this estate states that one problem arose out of
     * another, so it is the only complication signal that exists. It therefore measures what
     * clinicians <em>recorded</em> as a complication, not what happened: an unlinked diabetic foot
     * ulcer is invisible here. The service says so on the response.</p>
     *
     * <p>Restricted to {@code targetType = 'PROBLEM'}, the only target type whose reference is a real
     * foreign key. Links pointing at anything else are counted separately by
     * {@link #complicationLinksWithNonProblemTarget} rather than dropped, so the total is never
     * quietly smaller than the number of links recorded.</p>
     */
    @Query("""
           SELECT target.code, target.display, source.code, source.display, COUNT(l)
             FROM ProblemLinkEntity l, ProblemEntity source, ProblemEntity target
            WHERE l.tenantId = :tenantId
              AND l.linkType = 'COMPLICATION_OF'
              AND l.targetType = 'PROBLEM'
              AND l.problemId = source.problemId
              AND l.targetProblemId = target.problemId
              AND l.createdAt >= :from
              AND l.createdAt < :until
            GROUP BY target.code, target.display, source.code, source.display
            ORDER BY COUNT(l) DESC
           """)
    List<Object[]> complicationCounts(@Param("tenantId") UUID tenantId,
                                      @Param("from") OffsetDateTime from,
                                      @Param("until") OffsetDateTime until);

    /** COMPLICATION_OF links whose target is not a local problem row — reported, never dropped. */
    @Query("""
           SELECT COUNT(l)
             FROM ProblemLinkEntity l
            WHERE l.tenantId = :tenantId
              AND l.linkType = 'COMPLICATION_OF'
              AND l.targetType <> 'PROBLEM'
              AND l.createdAt >= :from
              AND l.createdAt < :until
           """)
    long complicationLinksWithNonProblemTarget(@Param("tenantId") UUID tenantId,
                                               @Param("from") OffsetDateTime from,
                                               @Param("until") OffsetDateTime until);
}
