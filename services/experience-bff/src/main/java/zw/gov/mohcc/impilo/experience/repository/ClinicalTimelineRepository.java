package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.ClinicalTimelineEntry;

import java.util.List;
import java.util.UUID;

public interface ClinicalTimelineRepository extends JpaRepository<ClinicalTimelineEntry, UUID> {

    Page<ClinicalTimelineEntry> findByTenantIdAndPatientIdOrderByOccurredAtDesc(String tenantId, UUID patientId, Pageable pageable);

    List<ClinicalTimelineEntry> findByTenantIdAndEncounterIdOrderByOccurredAtDesc(String tenantId, UUID encounterId);
}
