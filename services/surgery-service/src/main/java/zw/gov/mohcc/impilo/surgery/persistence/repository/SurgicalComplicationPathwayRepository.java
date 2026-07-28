package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalComplicationPathwayEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalComplicationPathwayRepository extends JpaRepository<SurgicalComplicationPathwayEntity, UUID> {

    Optional<SurgicalComplicationPathwayEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SurgicalComplicationPathwayEntity> findBySurgicalEpisodeIdAndTenantIdOrderByRecognisedAtDesc(
            UUID surgicalEpisodeId, UUID tenantId);
}
