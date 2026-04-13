package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.ClinicalNote;

import java.util.List;
import java.util.UUID;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    List<ClinicalNote> findByEncounterId(UUID encounterId);

    Page<ClinicalNote> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);
}
