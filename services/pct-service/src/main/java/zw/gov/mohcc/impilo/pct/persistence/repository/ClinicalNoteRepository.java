package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalNoteEntity;

import java.util.UUID;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNoteEntity, Long> {

    Page<ClinicalNoteEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(
            UUID tenantId, String patientId, Pageable pageable);
}
