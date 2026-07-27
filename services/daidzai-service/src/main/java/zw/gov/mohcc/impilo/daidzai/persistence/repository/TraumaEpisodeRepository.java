package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TraumaEpisodeRepository extends JpaRepository<TraumaEpisodeEntity, UUID> {
    Optional<TraumaEpisodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    /** Idempotency anchor for the dual-entry mint: one episode per (tenant, origin_key). */
    Optional<TraumaEpisodeEntity> findByTenantIdAndOriginKey(UUID tenantId, String originKey);
    Optional<TraumaEpisodeEntity> findByTenantIdAndIncidentId(UUID tenantId, UUID incidentId);

    /**
     * The V-3 enforcement sweep: OPEN episodes that reached a facility phase without a PCT anchor.
     * Backs {@code idx_dai_trauma_episode_unanchored}. This is the monitoring that replaced the
     * schema CHECK V201 withdrew — it surfaces a missing link rather than blocking the phase write.
     */
    @Query("select e from TraumaEpisodeEntity e where e.tenantId = :tenantId "
            + "and e.status = 'OPEN' and e.pctJourneyId is null "
            + "and e.currentPhase not in ('INCIDENT','DISPATCH','PREHOSPITAL')")
    java.util.List<TraumaEpisodeEntity> findUnanchoredFacilityPhase(@Param("tenantId") UUID tenantId);

    /** VITO merge repoint: move the subject anchor from the tombstoned to the surviving CPID. */
    @Modifying
    @Query("update TraumaEpisodeEntity e set e.subjectCpid = :survivor "
            + "where e.tenantId = :tenantId and e.subjectCpid = :merged")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                               @Param("merged") String mergedHealthId,
                               @Param("survivor") String survivorHealthId);
}
