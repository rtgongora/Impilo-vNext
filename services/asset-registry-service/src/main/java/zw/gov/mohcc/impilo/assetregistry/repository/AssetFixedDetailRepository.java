package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetFixedDetailEntity;

import java.util.Optional;
import java.util.UUID;

public interface AssetFixedDetailRepository extends JpaRepository<AssetFixedDetailEntity, Long> {
    Optional<AssetFixedDetailEntity> findByAssetId(UUID assetId);
}
