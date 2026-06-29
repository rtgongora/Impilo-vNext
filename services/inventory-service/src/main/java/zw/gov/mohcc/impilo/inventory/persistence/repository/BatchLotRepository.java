package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BatchLotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Dura batch/lot records.
 */
@Repository
public interface BatchLotRepository extends JpaRepository<BatchLotEntity, UUID> {

    Optional<BatchLotEntity> findByTenantIdAndItemIdAndBatchNumber(UUID tenantId, UUID itemId, String batchNumber);

    List<BatchLotEntity> findByTenantIdAndItemCodeOrderByExpiryDateAsc(UUID tenantId, String itemCode);

    /**
     * Near-expiry active batches: expiry on or before the cutoff date.
     */
    @Query("""
            SELECT b FROM BatchLotEntity b
            WHERE b.tenantId = :tenantId
              AND b.status = :status
              AND b.expiryDate IS NOT NULL
              AND b.expiryDate <= :cutoff
            ORDER BY b.expiryDate ASC
            """)
    List<BatchLotEntity> findNearExpiry(@Param("tenantId") UUID tenantId,
                                        @Param("status") BatchLotStatus status,
                                        @Param("cutoff") LocalDate cutoff);
}
