package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
