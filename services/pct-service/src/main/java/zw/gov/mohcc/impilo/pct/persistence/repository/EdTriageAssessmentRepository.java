package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdTriageAssessmentEntity;

import java.util.List;
import java.util.UUID;

public interface EdTriageAssessmentRepository extends JpaRepository<EdTriageAssessmentEntity, Long> {
    List<EdTriageAssessmentEntity> findByVisitIdOrderByCreatedAtDesc(UUID visitId);
}
