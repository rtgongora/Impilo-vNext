package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    List<AssignmentEntity> findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            UUID tenantId, String subjectType, String subjectId);

    List<AssignmentEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<AssignmentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
