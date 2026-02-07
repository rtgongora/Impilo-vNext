package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.LedgerEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEventEntity, UUID> {

    List<LedgerEventEntity> findByFacilityIdAndStoreIdAndItemCode(
            UUID facilityId, UUID storeId, String itemCode);

    Optional<LedgerEventEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(e.qtyDelta), 0) FROM LedgerEventEntity e " +
           "WHERE e.facilityId = :facilityId AND e.storeId = :storeId AND e.itemCode = :itemCode")
    int getStockBalance(@Param("facilityId") UUID facilityId,
                        @Param("storeId") UUID storeId,
                        @Param("itemCode") String itemCode);

    Page<LedgerEventEntity> findByTenantIdAndFacilityIdAndStoreIdAndItemCode(
            UUID tenantId, UUID facilityId, UUID storeId, String itemCode, Pageable pageable);

    Page<LedgerEventEntity> findByTenantIdAndFacilityIdAndStoreId(
            UUID tenantId, UUID facilityId, UUID storeId, Pageable pageable);

    Page<LedgerEventEntity> findByFacilityIdAndStoreIdOrderByCreatedAtDesc(
            UUID facilityId, UUID storeId, Pageable pageable);
}
