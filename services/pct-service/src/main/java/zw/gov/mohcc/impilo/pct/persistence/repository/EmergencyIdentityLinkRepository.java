package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyIdentityLinkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyIdentityLinkRepository extends JpaRepository<EmergencyIdentityLinkEntity, UUID> {

    /** The currently-active resolution for an episode — at most one, enforced by uq_eil_active_per_episode. */
    Optional<EmergencyIdentityLinkEntity> findByTenantIdAndEpisodeIdAndSupersededByIsNull(
            UUID tenantId, UUID episodeId);

    /** Full history, most recent first — both live and superseded rows, nothing lost. */
    List<EmergencyIdentityLinkEntity> findByTenantIdAndEpisodeIdOrderByResolvedAtDesc(
            UUID tenantId, UUID episodeId);
}
