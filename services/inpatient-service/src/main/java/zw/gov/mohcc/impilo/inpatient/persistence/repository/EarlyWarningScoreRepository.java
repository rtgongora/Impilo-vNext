package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EarlyWarningScoreEntity;

import java.util.List;
import java.util.UUID;

public interface EarlyWarningScoreRepository extends JpaRepository<EarlyWarningScoreEntity, UUID> {
    List<EarlyWarningScoreEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(UUID tenantId, String subjectCpid);
}
