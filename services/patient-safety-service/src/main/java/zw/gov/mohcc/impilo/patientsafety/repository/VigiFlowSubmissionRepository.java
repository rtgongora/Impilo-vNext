package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.VigiFlowSubmissionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface VigiFlowSubmissionRepository extends JpaRepository<VigiFlowSubmissionEntity, UUID> {
    List<VigiFlowSubmissionEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
