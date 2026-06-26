package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningSpaceEntity;

public interface LearningSpaceRepository extends JpaRepository<LearningSpaceEntity, UUID> {

    Optional<LearningSpaceEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LearningSpaceEntity> findByTenantIdAndProviderIdOrderByCreatedAtDesc(UUID tenantId, UUID providerId);

    List<LearningSpaceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
