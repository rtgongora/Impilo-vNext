package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCohortEntity;

public interface LearningCohortRepository extends JpaRepository<LearningCohortEntity, UUID> {

    Optional<LearningCohortEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<LearningCohortEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<LearningCohortEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
