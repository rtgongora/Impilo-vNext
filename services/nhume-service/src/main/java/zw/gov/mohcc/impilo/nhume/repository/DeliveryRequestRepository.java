package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequestEntity, UUID> {

    Optional<DeliveryRequestEntity> findByTenantIdAndReferenceCode(UUID tenantId, String referenceCode);

    @Query("SELECT d FROM DeliveryRequestEntity d WHERE d.tenantId = :tenantId"
            + " AND (:status IS NULL OR d.status = :status)"
            + " AND (:facilityId IS NULL OR d.requestingFacilityId = :facilityId)"
            + " AND (:deliveryType IS NULL OR d.deliveryType = :deliveryType)"
            + " AND (:priority IS NULL OR d.priority = :priority)"
            + " ORDER BY d.createdAt DESC")
    Page<DeliveryRequestEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                              @Param("status") String status,
                                              @Param("facilityId") String facilityId,
                                              @Param("deliveryType") String deliveryType,
                                              @Param("priority") String priority,
                                              Pageable pageable);

    @Query("SELECT d FROM DeliveryRequestEntity d WHERE d.createdAt <= :asOf ORDER BY d.createdAt DESC")
    Page<DeliveryRequestEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);

    @Query("SELECT d FROM DeliveryRequestEntity d WHERE d.tenantId = :tenantId AND d.slaDueAt < :now"
            + " AND d.status NOT IN ('DELIVERED','FAILED','CANCELLED','CLOSED','REJECTED','RETURNED')")
    List<DeliveryRequestEntity> findSlaBreached(@Param("tenantId") UUID tenantId,
                                                @Param("now") OffsetDateTime now);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantId(UUID tenantId);
}
