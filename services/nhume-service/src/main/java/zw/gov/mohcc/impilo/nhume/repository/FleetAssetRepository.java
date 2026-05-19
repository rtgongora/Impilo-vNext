package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.nhume.domain.FleetAssetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetAssetRepository extends JpaRepository<FleetAssetEntity, UUID> {

    Optional<FleetAssetEntity> findByTenantIdAndAssetCode(UUID tenantId, String assetCode);

    @Query("SELECT a FROM FleetAssetEntity a WHERE a.tenantId = :tenantId"
            + " AND (:mode IS NULL OR a.modeCategory = :mode)"
            + " AND (:availability IS NULL OR a.availabilityStatus = :availability)"
            + " AND (:facilityRef IS NULL OR a.homeFacilityRef = :facilityRef)"
            + " AND a.active = TRUE"
            + " ORDER BY a.displayName ASC")
    Page<FleetAssetEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                        @Param("mode") String mode,
                                        @Param("availability") String availability,
                                        @Param("facilityRef") String facilityRef,
                                        Pageable pageable);

    @Query("SELECT a FROM FleetAssetEntity a WHERE a.tenantId = :tenantId AND a.active = TRUE"
            + " AND a.availabilityStatus = 'AVAILABLE'")
    List<FleetAssetEntity> findAvailable(@Param("tenantId") UUID tenantId);

    long countByTenantIdAndActiveTrue(UUID tenantId);
}
