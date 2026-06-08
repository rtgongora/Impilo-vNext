package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodOrderRepository extends JpaRepository<BloodOrderEntity, Long> {
    Optional<BloodOrderEntity> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);
    List<BloodOrderEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId);
    List<BloodOrderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
