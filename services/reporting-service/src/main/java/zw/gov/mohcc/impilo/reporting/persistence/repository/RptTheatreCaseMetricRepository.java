package zw.gov.mohcc.impilo.reporting.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.reporting.persistence.entity.RptTheatreCaseMetricEntity;

import java.util.Optional;
import java.util.UUID;

public interface RptTheatreCaseMetricRepository extends JpaRepository<RptTheatreCaseMetricEntity, Long> {

    Optional<RptTheatreCaseMetricEntity> findByTenantIdAndEpisodeId(UUID tenantId, UUID episodeId);
}
