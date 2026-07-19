package zw.gov.mohcc.impilo.datagovernance.deid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidDatasetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeidDatasetRepository extends JpaRepository<DeidDatasetEntity, UUID> {

    List<DeidDatasetEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<DeidDatasetEntity> findByTenantIdAndDatasetCode(UUID tenantId, String datasetCode);
}
