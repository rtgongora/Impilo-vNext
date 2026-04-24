package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningPathEntity;

public interface LearningPathRepository extends JpaRepository<LearningPathEntity, UUID> {

    Optional<LearningPathEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
