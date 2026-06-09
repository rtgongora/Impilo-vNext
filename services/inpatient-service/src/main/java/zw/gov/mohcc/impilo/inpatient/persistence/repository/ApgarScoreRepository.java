package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ApgarScoreEntity;

import java.util.List;
import java.util.UUID;

public interface ApgarScoreRepository extends JpaRepository<ApgarScoreEntity, UUID> {
    List<ApgarScoreEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(UUID tenantId, String subjectCpid);
}
