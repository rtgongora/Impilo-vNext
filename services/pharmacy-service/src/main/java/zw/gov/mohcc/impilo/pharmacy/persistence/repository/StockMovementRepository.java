package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.StockMovementEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovementEntity, UUID> {

    List<StockMovementEntity> findByFacilityIdAndStoreIdAndItemCodeOrderByCreatedAtDesc(
            UUID facilityId, String storeId, String itemCode);

    List<StockMovementEntity> findByRefTypeAndRefId(String refType, String refId);

    @Query("SELECT COALESCE(SUM(s.qtyDelta), 0) FROM StockMovementEntity s " +
           "WHERE s.facilityId = :facilityId AND s.storeId = :storeId AND s.itemCode = :itemCode")
    int getStockBalance(@Param("facilityId") UUID facilityId,
                        @Param("storeId") String storeId,
                        @Param("itemCode") String itemCode);

    @Query("SELECT COALESCE(SUM(s.qtyDelta), 0) FROM StockMovementEntity s " +
           "WHERE s.facilityId = :facilityId AND s.storeId = :storeId " +
           "AND s.itemCode = :itemCode AND s.batchNumber = :batch")
    int getStockBalanceByBatch(@Param("facilityId") UUID facilityId,
                               @Param("storeId") String storeId,
                               @Param("itemCode") String itemCode,
                               @Param("batch") String batch);
}
