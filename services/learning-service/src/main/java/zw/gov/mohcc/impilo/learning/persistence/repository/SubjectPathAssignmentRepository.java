package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.SubjectPathAssignmentEntity;

public interface SubjectPathAssignmentRepository extends JpaRepository<SubjectPathAssignmentEntity, UUID> {

    boolean existsByTenantIdAndSubjectTypeAndSubjectIdAndPathId(
            UUID tenantId, String subjectType, String subjectId, UUID pathId);
}
