package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyDispositionEntity;

public interface EmergencyDispositionRepository extends JpaRepository<EmergencyDispositionEntity, UUID> {

    Optional<EmergencyDispositionEntity> findByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId);

    boolean existsByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId);
}
