package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedurePreopAssessmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedurePreopAssessmentRepository extends JpaRepository<ProcedurePreopAssessmentEntity, UUID> {
    List<ProcedurePreopAssessmentEntity> findByEpisodeIdOrderByAssessedAtAsc(UUID episodeId);
    Optional<ProcedurePreopAssessmentEntity> findByEpisodeIdAndAssessmentType(UUID episodeId, String assessmentType);
}
