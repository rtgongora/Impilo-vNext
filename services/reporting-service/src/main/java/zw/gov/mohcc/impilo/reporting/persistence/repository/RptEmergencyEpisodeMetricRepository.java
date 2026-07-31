package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptEmergencyEpisodeMetricEntity;

import java.util.Optional;
import java.util.UUID;

public interface RptEmergencyEpisodeMetricRepository extends JpaRepository<RptEmergencyEpisodeMetricEntity, Long> {

    Optional<RptEmergencyEpisodeMetricEntity> findByTenantIdAndEpisodeId(UUID tenantId, UUID episodeId);
}
