package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CompletionRuleEntity;

public interface CompletionRuleRepository extends JpaRepository<CompletionRuleEntity, UUID> {

    List<CompletionRuleEntity> findByTenantIdAndCourseIdOrderByCreatedAtAsc(UUID tenantId, UUID courseId);

    Optional<CompletionRuleEntity> findByTenantIdAndCourseIdAndRuleType(UUID tenantId, UUID courseId, String ruleType);

    Optional<CompletionRuleEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCourseId(UUID tenantId, UUID courseId);
}
