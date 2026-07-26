package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FunctionalAssessmentEntity;

import java.util.List;
import java.util.UUID;

public interface FunctionalAssessmentRepository extends JpaRepository<FunctionalAssessmentEntity, UUID> {

    List<FunctionalAssessmentEntity> findByTenantIdAndSubjectCpidOrderByAssessmentDateDesc(UUID tenantId, String subjectCpid);
}
