package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.FleetAssetEntity;

import java.util.List;
import java.util.UUID;

public interface FleetAssetRepository extends JpaRepository<FleetAssetEntity, UUID> {
    List<FleetAssetEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
