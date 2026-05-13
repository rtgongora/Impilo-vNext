package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentQuestionEntity;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestionEntity, UUID> {
    List<AssessmentQuestionEntity> findByAssessmentIdOrderBySequenceNoAsc(UUID assessmentId);
}
