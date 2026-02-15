package zw.gov.mohcc.impilo.pipeline.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestionSourceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionSourceRepository extends JpaRepository<IngestionSourceEntity, String> {

    Optional<IngestionSourceEntity> findByTenantIdAndSourceId(UUID tenantId, String sourceId);

    List<IngestionSourceEntity> findByTenantId(UUID tenantId);

    List<IngestionSourceEntity> findByTenantIdAndEnabledTrue(UUID tenantId);
}
