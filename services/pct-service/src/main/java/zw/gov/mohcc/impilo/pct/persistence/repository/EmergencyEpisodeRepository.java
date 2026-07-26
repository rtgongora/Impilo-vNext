package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyEpisodeRepository extends JpaRepository<EmergencyEpisodeEntity, UUID> {

    Optional<EmergencyEpisodeEntity> findByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId);

    /** The idempotent-mint lookup. Mirrors uq_emergency_episode_entry_source. */
    Optional<EmergencyEpisodeEntity> findByTenantIdAndEntryRouteAndEntrySourceRef(
            UUID tenantId, String entryRoute, String entrySourceRef);

    /** The operational board. */
    List<EmergencyEpisodeEntity> findByTenantIdAndFacilityIdAndStateInOrderByArrivedAtDesc(
            UUID tenantId, UUID facilityId, List<String> states);

    /**
     * Repoint a provisional identity onto a confirmed CPID after a VITO merge.
     *
     * <p>This updates an attribute. It never re-parents a row, because every emergency clinical
     * record anchors on {@code episode_id} rather than on the cpid — which is what makes a merge
     * incapable of losing orders, results, medicines, procedures or notes.
     */
    @Modifying
    @Query("update EmergencyEpisodeEntity e set e.subjectCpid = :confirmed, "
            + "e.identityMode = 'KNOWN', e.identityResolvedAt = CURRENT_TIMESTAMP, "
            + "e.updatedAt = CURRENT_TIMESTAMP "
            + "where e.tenantId = :tenantId and e.subjectCpid = :provisional")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                           @Param("provisional") String provisional,
                           @Param("confirmed") String confirmed);
}
