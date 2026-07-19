package zw.gov.mohcc.impilo.datagovernance.deid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidRunEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeidRunRepository extends JpaRepository<DeidRunEntity, UUID> {

    Optional<DeidRunEntity> findByRunIdAndTenantId(UUID runId, UUID tenantId);

    List<DeidRunEntity> findByTenantIdAndDatasetIdOrderByRequestedAtDesc(UUID tenantId, UUID datasetId);
}
