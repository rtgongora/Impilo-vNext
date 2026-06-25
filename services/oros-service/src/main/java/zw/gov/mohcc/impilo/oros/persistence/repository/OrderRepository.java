package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    Optional<OrderEntity> findByOrderId(String orderId);

    List<OrderEntity> findByPatientCpidOrderByPlacedAtDesc(String patientCpid);

    /**
     * Finds all orders for a patient within a specific tenant.
     *
     * @param tenantId     the tenant UUID
     * @param patientCpid  the patient's CPID
     * @return list of matching orders
     */
    List<OrderEntity> findByTenantIdAndPatientCpid(UUID tenantId, String patientCpid);

    Page<OrderEntity> findByFacilityIdAndOrderTypeAndStatusAndPriority(
            UUID facilityId, OrderType type, OrderStatus status, OrderPriority priority, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndOrderTypeAndStatus(
            UUID facilityId, OrderType type, OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndOrderType(
            UUID facilityId, OrderType type, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndStatus(
            UUID facilityId, OrderStatus status, Pageable pageable);

    Page<OrderEntity> findByFacilityId(UUID facilityId, Pageable pageable);

    Page<OrderEntity> findByFacilityIdAndWorkspaceIdAndOrderTypeAndStatusAndPriority(
            UUID facilityId, UUID workspaceId, OrderType type, OrderStatus status,
            OrderPriority priority, Pageable pageable);

    /**
     * Null-tolerant order search within a tenant, filtering by any combination of patient
     * (client), requester (matched against either the placing actor or the referring provider),
     * coarse status, and order type. Most-recent first.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.tenantId = :tenantId
              AND (:patientCpid IS NULL OR o.patientCpid = :patientCpid)
              AND (:status IS NULL OR o.status = :status)
              AND (:orderType IS NULL OR o.orderType = :orderType)
              AND (:requester IS NULL OR o.placedBy = :requester OR o.referringProviderId = :requester)
            ORDER BY o.placedAt DESC, o.createdAt DESC
            """)
    List<OrderEntity> search(@Param("tenantId") UUID tenantId,
                             @Param("patientCpid") String patientCpid,
                             @Param("requester") String requester,
                             @Param("status") OrderStatus status,
                             @Param("orderType") OrderType orderType);
}
