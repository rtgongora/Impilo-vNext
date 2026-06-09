package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ClinicalChartEntryEntity;

import java.util.List;
import java.util.UUID;

public interface ClinicalChartEntryRepository extends JpaRepository<ClinicalChartEntryEntity, UUID> {
    List<ClinicalChartEntryEntity> findByTenantIdAndSubjectCpidAndChartTypeOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid, String chartType);

    List<ClinicalChartEntryEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);
}
