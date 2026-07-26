package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PatientDocumentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads are always tenant-scoped; every finder leads with {@code tenantId}. */
@Repository
public interface PatientDocumentRepository extends JpaRepository<PatientDocumentEntity, UUID> {

    List<PatientDocumentEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);

    List<PatientDocumentEntity> findByTenantIdAndSubjectCpidAndDocumentTypeOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid, String documentType);

    Optional<PatientDocumentEntity> findByTenantIdAndDocumentId(UUID tenantId, UUID documentId);
}
