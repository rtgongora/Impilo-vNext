package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseAssignmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseAssignmentRepository extends JpaRepository<CaseAssignmentEntity, UUID> {

    List<CaseAssignmentEntity> findByCaseIdOrderByAssignedAtDesc(UUID caseId);

    List<CaseAssignmentEntity> findByCaseIdAndActiveTrue(UUID caseId);
}
