package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProgressEntity;

public interface LearningProgressRepository extends JpaRepository<LearningProgressEntity, UUID> {

    Optional<LearningProgressEntity> findByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(
            UUID tenantId, String subjectType, String subjectId, UUID resourceId);
}
