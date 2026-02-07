package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnHandRepository extends JpaRepository<OnHandEntity, UUID> {

    List<OnHandEntity> findByFacilityIdAndStoreId(UUID facilityId, UUID storeId);

    List<OnHandEntity> findByFacilityIdAndStoreIdAndItemCode(UUID facilityId, UUID storeId, String itemCode);

    List<OnHandEntity> findByFacilityIdAndStoreIdAndBinId(UUID facilityId, UUID storeId, UUID binId);

    List<OnHandEntity> findByFacilityIdAndStoreIdAndItemCodeAndQtyOnHandGreaterThan(
            UUID facilityId, UUID storeId, String itemCode, int qtyOnHand);

    Optional<OnHandEntity> findByFacilityIdAndStoreIdAndItemCodeAndBatchAndExpiry(
            UUID facilityId, UUID storeId, String itemCode, String batch, LocalDate expiry);

    Page<OnHandEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId, Pageable pageable);

    Page<OnHandEntity> findByTenantIdAndFacilityIdAndStoreId(
            UUID tenantId, UUID facilityId, UUID storeId, Pageable pageable);

    Page<OnHandEntity> findByTenantIdAndFacilityIdAndStoreIdAndBinId(
            UUID tenantId, UUID facilityId, UUID storeId, UUID binId, Pageable pageable);

    Page<OnHandEntity> findByTenantIdAndFacilityIdAndStoreIdAndItemCode(
            UUID tenantId, UUID facilityId, UUID storeId, String itemCode, Pageable pageable);

    @Query("SELECT o FROM OnHandEntity o " +
           "WHERE o.tenantId = :tenantId AND o.facilityId = :facilityId " +
           "AND o.expiry IS NOT NULL AND o.expiry <= :threshold AND o.qtyOnHand > 0")
    List<OnHandEntity> findNearExpiry(@Param("tenantId") UUID tenantId,
                                      @Param("facilityId") UUID facilityId,
                                      @Param("threshold") LocalDate threshold);

    @Query("SELECT o FROM OnHandEntity o " +
           "WHERE o.tenantId = :tenantId AND o.facilityId = :facilityId " +
           "AND o.qtyOnHand <= 0")
    List<OnHandEntity> findStockouts(@Param("tenantId") UUID tenantId,
                                     @Param("facilityId") UUID facilityId);
}
