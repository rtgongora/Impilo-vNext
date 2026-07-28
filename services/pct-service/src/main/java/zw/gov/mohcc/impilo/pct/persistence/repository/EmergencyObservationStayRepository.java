package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyObservationStayEntity;

public interface EmergencyObservationStayRepository extends JpaRepository<EmergencyObservationStayEntity, UUID> {

    Optional<EmergencyObservationStayEntity> findByStayIdAndTenantId(UUID stayId, UUID tenantId);

    /**
     * Pre-check mirroring {@code uq_emergency_observation_stay_open}: at most one open stay per
     * episode. The partial unique index is the real guarantee; this gives the ordinary path a clear
     * error instead of relying on catching a constraint violation.
     */
    @Query("select s from EmergencyObservationStayEntity s where s.tenantId = :tenantId "
            + "and s.episodeId = :episodeId and s.endedAt is null")
    Optional<EmergencyObservationStayEntity> findOpen(@Param("tenantId") UUID tenantId,
                                                       @Param("episodeId") UUID episodeId);

    List<EmergencyObservationStayEntity> findByTenantIdAndEpisodeIdOrderByStartedAtDesc(UUID tenantId, UUID episodeId);
}
