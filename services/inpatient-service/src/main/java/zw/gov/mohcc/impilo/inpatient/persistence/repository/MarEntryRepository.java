package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.MarEntryEntity;

import java.util.List;
import java.util.UUID;

public interface MarEntryRepository extends JpaRepository<MarEntryEntity, UUID> {
    List<MarEntryEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(UUID tenantId, String subjectCpid);

    java.util.Optional<MarEntryEntity> findByTenantIdAndSubjectCpidAndPrescriptionId(
            UUID tenantId, String subjectCpid, String prescriptionId);
}
