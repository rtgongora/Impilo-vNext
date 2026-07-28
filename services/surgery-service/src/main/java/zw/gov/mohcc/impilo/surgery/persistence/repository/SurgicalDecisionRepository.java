package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalDecisionEntity;

import java.util.Optional;
import java.util.UUID;

public interface SurgicalDecisionRepository extends JpaRepository<SurgicalDecisionEntity, UUID> {

    Optional<SurgicalDecisionEntity> findBySurgicalEpisodeIdAndTenantId(UUID surgicalEpisodeId, UUID tenantId);
}
