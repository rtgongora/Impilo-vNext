package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
    Page<OrderEntity> findByTenantIdAndStatus(UUID tenantId, OrderStatus status, Pageable pageable);
    Page<OrderEntity> findByTenantIdAndOrderType(UUID tenantId, OrderType orderType, Pageable pageable);
    Page<OrderEntity> findByPatientCpidOrderByCreatedAtDesc(String patientCpid, Pageable pageable);
    Page<OrderEntity> findByVendorIdAndStatusIn(UUID vendorId, List<OrderStatus> statuses, Pageable pageable);
    Page<OrderEntity> findByFacilityIdAndStatus(UUID facilityId, OrderStatus status, Pageable pageable);
    List<OrderEntity> findByStatusAndTenantId(OrderStatus status, UUID tenantId);
}
