package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.ClinicalDocument;

import java.util.UUID;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, UUID> {

    Page<ClinicalDocument> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);
}
