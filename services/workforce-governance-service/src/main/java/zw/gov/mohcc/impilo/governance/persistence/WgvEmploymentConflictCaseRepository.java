package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WgvEmploymentConflictCaseRepository
        extends JpaRepository<WgvEmploymentConflictCaseEntity, UUID> {

    Optional<WgvEmploymentConflictCaseEntity> findByWorkflowInstanceId(UUID workflowInstanceId);

    List<WgvEmploymentConflictCaseEntity> findByTenantIdAndEcNumberAndStatusIn(
            UUID tenantId, String ecNumber, List<String> statuses);

    List<WgvEmploymentConflictCaseEntity> findByTenantIdAndSubjectRefAndStatusIn(
            UUID tenantId, String subjectRef, List<String> statuses);
}
