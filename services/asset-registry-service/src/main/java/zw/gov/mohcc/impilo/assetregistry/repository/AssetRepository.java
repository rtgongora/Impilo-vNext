package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.assetregistry.domain.AssetEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {

    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId"
            + " AND (:facilityRef IS NULL OR a.facilityRef = :facilityRef)"
            + " AND (:status IS NULL OR a.status = :status)")
    Page<AssetEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                   @Param("facilityRef") String facilityRef,
                                   @Param("status") String status,
                                   Pageable pageable);

    @Query("SELECT a FROM AssetEntity a WHERE a.updatedAt <= :asOf ORDER BY a.assetId")
    Page<AssetEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);
}
