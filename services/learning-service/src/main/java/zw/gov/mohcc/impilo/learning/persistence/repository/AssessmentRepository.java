package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.AssessmentEntity;

public interface AssessmentRepository extends JpaRepository<AssessmentEntity, UUID> {
    Optional<AssessmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AssessmentEntity> findByTenantIdAndCourseIdAndStatus(UUID tenantId, UUID courseId, String status);
}
