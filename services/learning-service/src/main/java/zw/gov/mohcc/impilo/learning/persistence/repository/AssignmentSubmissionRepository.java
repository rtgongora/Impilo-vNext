package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssignmentSubmissionEntity;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmissionEntity, UUID> {

    Optional<AssignmentSubmissionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AssignmentSubmissionEntity> findByTenantIdAndAssignmentIdOrderBySubmissionNoAsc(UUID tenantId, UUID assignmentId);

    List<AssignmentSubmissionEntity> findByTenantIdAndMarkingStatusOrderByCreatedAtAsc(
            UUID tenantId, String markingStatus, Pageable pageable);

    List<AssignmentSubmissionEntity> findByAssignmentIdAndSubjectTypeAndSubjectId(
            UUID assignmentId, String subjectType, String subjectId);
}
