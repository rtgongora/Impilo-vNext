package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImagingLinkEntity;

import java.util.List;
import java.util.UUID;

public interface ImagingLinkRepository extends JpaRepository<ImagingLinkEntity, Long> {

    List<ImagingLinkEntity> findByTenantIdAndEncounterIdOrderByCreatedAtDesc(UUID tenantId, Long encounterId);

    List<ImagingLinkEntity> findByTenantIdAndJourneyIdOrderByCreatedAtDesc(UUID tenantId, String journeyId);
}
