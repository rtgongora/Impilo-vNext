package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCompletionEntity;

public interface LearningCompletionRepository extends JpaRepository<LearningCompletionEntity, UUID> {

    boolean existsByTenantIdAndSourceTypeAndSourceRef(UUID tenantId, String sourceType, String sourceRef);

    boolean existsByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(
            UUID tenantId, String subjectType, String subjectId, UUID resourceId);

    List<LearningCompletionEntity> findByTenantIdAndSubjectTypeAndSubjectIdOrderByCompletionDateDesc(
            UUID tenantId, String subjectType, String subjectId, Pageable pageable);
}
