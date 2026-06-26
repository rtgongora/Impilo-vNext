package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.CaseActionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseActionRepository extends JpaRepository<CaseActionEntity, UUID> {
    List<CaseActionEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
