package zw.gov.mohcc.impilo.pct.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyOrderSetInstanceEntity;

public interface EmergencyOrderSetInstanceRepository extends JpaRepository<EmergencyOrderSetInstanceEntity, UUID> {
    Optional<EmergencyOrderSetInstanceEntity> findByInstanceIdAndTenantId(UUID instanceId, UUID tenantId);
    List<EmergencyOrderSetInstanceEntity> findByTenantIdAndEpisodeIdOrderByInvokedAtDesc(UUID tenantId, UUID episodeId);
}
