package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaTrackingAssetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NdilaTrackingAssetRepository extends JpaRepository<NdilaTrackingAssetEntity, UUID> {

    Optional<NdilaTrackingAssetEntity> findByTenantIdAndAssetId(UUID tenantId, String assetId);

    List<NdilaTrackingAssetEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
