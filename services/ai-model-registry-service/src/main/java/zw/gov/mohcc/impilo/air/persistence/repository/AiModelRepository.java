package zw.gov.mohcc.impilo.air.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelEntity;

@Repository
public interface AiModelRepository extends JpaRepository<AiModelEntity, Long> {

    Optional<AiModelEntity> findByTenantIdAndModelId(UUID tenantId, UUID modelId);

    List<AiModelEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
