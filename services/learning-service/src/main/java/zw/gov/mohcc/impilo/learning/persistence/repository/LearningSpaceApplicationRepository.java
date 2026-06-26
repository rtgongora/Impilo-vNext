package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningSpaceApplicationEntity;

public interface LearningSpaceApplicationRepository extends JpaRepository<LearningSpaceApplicationEntity, UUID> {

    Optional<LearningSpaceApplicationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LearningSpaceApplicationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<LearningSpaceApplicationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
}
