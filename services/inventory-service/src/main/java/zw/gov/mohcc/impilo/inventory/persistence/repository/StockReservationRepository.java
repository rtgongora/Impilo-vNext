package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Dura stock reservations.
 */
@Repository
public interface StockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {

    List<StockReservationEntity> findByTenantIdAndFacilityIdAndStoreIdAndItemCodeAndStatus(
            UUID tenantId, UUID facilityId, UUID storeId, String itemCode, ReservationStatus status);

    List<StockReservationEntity> findByTenantIdAndRefTypeAndRefId(UUID tenantId, String refType, String refId);

    /**
     * Sum of quantity held by active reservations for an item at a store.
     * Returns 0 when there are no active reservations.
     */
    @Query("""
            SELECT COALESCE(SUM(r.qty), 0) FROM StockReservationEntity r
            WHERE r.tenantId = :tenantId
              AND r.facilityId = :facilityId
              AND r.storeId = :storeId
              AND r.itemCode = :itemCode
              AND r.status = zw.gov.mohcc.impilo.inventory.domain.ReservationStatus.ACTIVE
            """)
    int sumActiveReserved(@Param("tenantId") UUID tenantId,
                          @Param("facilityId") UUID facilityId,
                          @Param("storeId") UUID storeId,
                          @Param("itemCode") String itemCode);
}
