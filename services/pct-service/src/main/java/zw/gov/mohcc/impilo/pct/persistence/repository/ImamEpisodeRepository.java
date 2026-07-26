package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImamEpisodeRepository extends JpaRepository<ImamEpisodeEntity, UUID> {

    List<ImamEpisodeEntity> findByTenantIdAndSubjectCpidOrderByEnrolledAtDesc(UUID tenantId, String subjectCpid);

    Optional<ImamEpisodeEntity> findByTenantIdAndSubjectCpidAndStatus(UUID tenantId, String subjectCpid, String status);

    Optional<ImamEpisodeEntity> findByTenantIdAndImamEpisodeId(UUID tenantId, UUID imamEpisodeId);

    /**
     * The tracing queue. Open episodes whose next review has come and gone, oldest overdue first,
     * so the child who has been missing longest is at the top rather than buried.
     *
     * <p>An episode with a null {@code next_review_due} is included deliberately: an episode with no
     * schedule at all is not a child on track, it is a child who was enrolled and then dropped out
     * of the system's sight entirely.</p>
     */
    @Query("""
            SELECT e FROM ImamEpisodeEntity e
            WHERE e.tenantId = :tenantId
              AND e.status = 'ACTIVE'
              AND (:facilityId IS NULL OR e.facilityId = :facilityId)
              AND (e.nextReviewDue IS NULL OR e.nextReviewDue < :asOf)
            ORDER BY e.nextReviewDue ASC NULLS FIRST
            """)
    List<ImamEpisodeEntity> findOverdue(@Param("tenantId") UUID tenantId,
                                        @Param("facilityId") UUID facilityId,
                                        @Param("asOf") LocalDate asOf);

    @Query("""
            SELECT e FROM ImamEpisodeEntity e
            WHERE e.tenantId = :tenantId
              AND e.status = 'ACTIVE'
              AND (:facilityId IS NULL OR e.facilityId = :facilityId)
            ORDER BY e.enrolledAt DESC
            """)
    List<ImamEpisodeEntity> findActive(@Param("tenantId") UUID tenantId,
                                       @Param("facilityId") UUID facilityId);

    /**
     * Every tenant's overdue episodes, for the tracing sweep. Deliberately not tenant-scoped: the
     * sweep runs without a request and therefore without a tenant, and a per-tenant loop would need
     * a list of tenants that nothing maintains.
     */
    @Query("""
            SELECT e FROM ImamEpisodeEntity e
            WHERE e.status = 'ACTIVE'
              AND (e.nextReviewDue IS NULL OR e.nextReviewDue < :asOf)
            ORDER BY e.nextReviewDue ASC NULLS FIRST
            """)
    List<ImamEpisodeEntity> findAllOverdue(@Param("asOf") LocalDate asOf);
}
