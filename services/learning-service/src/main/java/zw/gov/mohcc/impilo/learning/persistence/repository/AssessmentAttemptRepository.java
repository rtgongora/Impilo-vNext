package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentAttemptEntity;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttemptEntity, UUID> {

    List<AssessmentAttemptEntity> findByAssessmentIdAndSubjectTypeAndSubjectIdOrderByAttemptNoDesc(
            UUID assessmentId, String subjectType, String subjectId);

    List<AssessmentAttemptEntity> findByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, String subjectId);

    long countByAssessmentIdAndSubjectTypeAndSubjectId(UUID assessmentId, String subjectType, String subjectId);

    Optional<AssessmentAttemptEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AssessmentAttemptEntity> findByTenantIdAndAssessmentId(UUID tenantId, UUID assessmentId);

    List<AssessmentAttemptEntity> findByTenantIdAndAssessmentIdAndManualReviewStatusOrderBySubmittedAtDesc(
            UUID tenantId, UUID assessmentId, String manualReviewStatus);
}
