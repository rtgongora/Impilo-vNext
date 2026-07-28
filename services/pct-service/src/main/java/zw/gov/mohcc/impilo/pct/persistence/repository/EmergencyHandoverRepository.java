package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyHandoverEntity;

public interface EmergencyHandoverRepository extends JpaRepository<EmergencyHandoverEntity, UUID> {

    Optional<EmergencyHandoverEntity> findByHandoverIdAndTenantId(UUID handoverId, UUID tenantId);

    /**
     * Pre-check mirroring {@code uq_emergency_handover_pending}: at most one live request per
     * episode. The partial unique index is the real guarantee; this lets the ordinary path give a
     * clear error instead of relying on catching a constraint violation.
     */
    @Query("select h from EmergencyHandoverEntity h where h.tenantId = :tenantId "
            + "and h.episodeId = :episodeId and h.status = 'PENDING'")
    Optional<EmergencyHandoverEntity> findPending(@Param("tenantId") UUID tenantId,
                                                  @Param("episodeId") UUID episodeId);

    List<EmergencyHandoverEntity> findByTenantIdAndEpisodeIdOrderByRequestedAtDesc(UUID tenantId, UUID episodeId);

    /** The accepting side's board: open requests for a target, oldest first. */
    @Query("select h from EmergencyHandoverEntity h where h.tenantId = :tenantId "
            + "and h.targetType = :targetType and h.status = 'PENDING' "
            + "order by h.requestedAt asc")
    List<EmergencyHandoverEntity> findPendingByTarget(@Param("tenantId") UUID tenantId,
                                                       @Param("targetType") String targetType);
}
