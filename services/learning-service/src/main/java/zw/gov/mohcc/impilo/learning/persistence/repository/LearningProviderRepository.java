package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProviderEntity;

public interface LearningProviderRepository extends JpaRepository<LearningProviderEntity, UUID> {

    Optional<LearningProviderEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LearningProviderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<LearningProviderEntity> findByTenantIdAndProviderKindOrderByCreatedAtDesc(
            UUID tenantId, String providerKind, Pageable pageable);
}
