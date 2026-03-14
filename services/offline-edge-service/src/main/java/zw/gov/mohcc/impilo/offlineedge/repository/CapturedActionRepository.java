package zw.gov.mohcc.impilo.offlineedge.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import java.util.List;
import java.util.UUID;

public interface CapturedActionRepository extends JpaRepository<CapturedActionEntity, UUID> {
    List<CapturedActionEntity> findByEntitlementIdAndStatusOrderBySequenceNumAsc(UUID entitlementId, String status);

    @Query("SELECT a FROM CapturedActionEntity a WHERE a.tenantId = :tenantId"
            + " AND (:status IS NULL OR a.status = :status) ORDER BY a.createdAt DESC")
    Page<CapturedActionEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                             @Param("status") String status, Pageable pageable);

    long countByEntitlementId(UUID entitlementId);
}
