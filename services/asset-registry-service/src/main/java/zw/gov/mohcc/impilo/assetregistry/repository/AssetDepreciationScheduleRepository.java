package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetDepreciationScheduleEntity;

import java.util.List;
import java.util.UUID;

public interface AssetDepreciationScheduleRepository extends JpaRepository<AssetDepreciationScheduleEntity, Long> {
    List<AssetDepreciationScheduleEntity> findByAssetIdOrderByPeriodDateAsc(UUID assetId);
}
