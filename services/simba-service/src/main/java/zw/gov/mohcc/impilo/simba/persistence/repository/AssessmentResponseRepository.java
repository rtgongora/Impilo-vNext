package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponseEntity, Long> {

    List<AssessmentResponseEntity> findByAssessmentIdOrderBySectionAsc(UUID assessmentId);

    Optional<AssessmentResponseEntity> findByAssessmentIdAndQuestionCode(UUID assessmentId, String questionCode);
}
