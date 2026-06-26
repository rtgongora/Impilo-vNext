package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssignmentEntity;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {

    Optional<AssignmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AssignmentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<AssignmentEntity> findByTenantIdAndCourseIdOrderByCreatedAtDesc(UUID tenantId, UUID courseId, Pageable pageable);

    List<AssignmentEntity> findByTenantIdAndCohortIdOrderByCreatedAtDesc(UUID tenantId, UUID cohortId, Pageable pageable);
}
