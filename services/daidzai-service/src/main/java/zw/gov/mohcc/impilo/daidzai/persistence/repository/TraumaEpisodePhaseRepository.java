package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodePhaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TraumaEpisodePhaseRepository extends JpaRepository<TraumaEpisodePhaseEntity, UUID> {

    List<TraumaEpisodePhaseEntity> findByTraumaEpisodeIdOrderByOccurredAtAsc(UUID traumaEpisodeId);

    /** Idempotency guard: has this phase-owner event already been recorded on the timeline? */
    Optional<TraumaEpisodePhaseEntity> findByTenantIdAndTraumaEpisodeIdAndOwnerServiceAndOwnerRefAndPhase(
            UUID tenantId, UUID traumaEpisodeId, String ownerService, String ownerRef, String phase);
}
