package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MedicalEpisodeRepository extends JpaRepository<MedicalEpisodeEntity, UUID> {

    List<MedicalEpisodeEntity> findByTenantIdAndSubjectCpidOrderByStartedOnDesc(
            UUID tenantId, String subjectCpid);

    List<MedicalEpisodeEntity> findByTenantIdAndSubjectCpidAndStatusInOrderByStartedOnDesc(
            UUID tenantId, String subjectCpid, Collection<String> statuses);

    /** Episodes opened in the window by type and status — the denominator for palliative access. */
    @Query("""
           SELECT e.episodeType, e.status, COUNT(e)
             FROM MedicalEpisodeEntity e
            WHERE e.tenantId = :tenantId
              AND (:facilityId IS NULL OR e.managingFacilityId = :facilityId)
              AND e.startedOn >= :from
              AND e.startedOn <= :to
            GROUP BY e.episodeType, e.status
           """)
    List<Object[]> episodeTypeCounts(@Param("tenantId") UUID tenantId,
                                     @Param("facilityId") String facilityId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * People with an episode opened in the window, optionally of one type.
     *
     * <p>Palliative access is a question about people, not episodes: a person with three palliative
     * episodes has been reached once. Counting episodes would make a service that re-opens episodes
     * look like a service that reaches more patients.</p>
     */
    @Query("""
           SELECT COUNT(DISTINCT e.subjectCpid)
             FROM MedicalEpisodeEntity e
            WHERE e.tenantId = :tenantId
              AND (:facilityId IS NULL OR e.managingFacilityId = :facilityId)
              AND (:episodeType IS NULL OR e.episodeType = :episodeType)
              AND e.startedOn >= :from
              AND e.startedOn <= :to
           """)
    long distinctSubjectsWithEpisode(@Param("tenantId") UUID tenantId,
                                     @Param("facilityId") String facilityId,
                                     @Param("episodeType") String episodeType,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Episode endings in the window by end reason — DIED among them.
     *
     * <p>End reason is kept apart from status for exactly this read: COMPLETED, TRANSFERRED,
     * LOST_TO_FOLLOW_UP and DIED all leave the status FINISHED, and an outcome indicator over status
     * alone cannot tell a cure from a death.</p>
     */
    @Query("""
           SELECT e.episodeType, e.endReason, COUNT(e)
             FROM MedicalEpisodeEntity e
            WHERE e.tenantId = :tenantId
              AND (:facilityId IS NULL OR e.managingFacilityId = :facilityId)
              AND e.endedOn IS NOT NULL
              AND e.endedOn >= :from
              AND e.endedOn <= :to
            GROUP BY e.episodeType, e.endReason
           """)
    List<Object[]> episodeEndReasonCounts(@Param("tenantId") UUID tenantId,
                                          @Param("facilityId") String facilityId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}
