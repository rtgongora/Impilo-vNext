package zw.gov.mohcc.impilo.air.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.air.persistence.entity.InferenceRecordEntity;

@Repository
public interface InferenceRecordRepository extends JpaRepository<InferenceRecordEntity, Long> {

    List<InferenceRecordEntity> findByTenantIdAndWorkflowRefOrderByCreatedAtDesc(UUID tenantId, String workflowRef);

    List<InferenceRecordEntity> findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            UUID tenantId, String subjectType, String subjectId);
}
