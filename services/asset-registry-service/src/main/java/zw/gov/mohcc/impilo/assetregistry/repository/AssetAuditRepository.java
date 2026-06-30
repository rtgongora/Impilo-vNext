package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetAuditEntity;

import java.util.List;
import java.util.UUID;

public interface AssetAuditRepository extends JpaRepository<AssetAuditEntity, UUID> {
    List<AssetAuditEntity> findByTenantIdAndFacilityRefOrderByOpenedAtDesc(UUID tenantId, String facilityRef);
}
