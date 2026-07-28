package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalFollowupEntity;

import java.util.Optional;
import java.util.UUID;

public interface SurgicalFollowupRepository extends JpaRepository<SurgicalFollowupEntity, UUID> {

    Optional<SurgicalFollowupEntity> findBySurgicalEpisodeIdAndTenantId(UUID surgicalEpisodeId, UUID tenantId);
}
