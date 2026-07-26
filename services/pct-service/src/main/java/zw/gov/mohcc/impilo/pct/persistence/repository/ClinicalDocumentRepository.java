package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocumentEntity, UUID> {

    List<ClinicalDocumentEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);

    List<ClinicalDocumentEntity> findByTenantIdAndSubjectCpidAndDocumentTypeOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid, String documentType);
}
