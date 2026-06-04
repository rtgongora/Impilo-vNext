package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageImagingLinkEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TriageImagingLinkRepository extends JpaRepository<TriageImagingLinkEntity, Long> {
    List<TriageImagingLinkEntity> findByTenantIdAndJourneyIdOrderByCreatedAtDesc(UUID tenantId, String journeyId);
    List<TriageImagingLinkEntity> findByTenantIdAndStudyIdOrderByCreatedAtDesc(UUID tenantId, Long studyId);
}
