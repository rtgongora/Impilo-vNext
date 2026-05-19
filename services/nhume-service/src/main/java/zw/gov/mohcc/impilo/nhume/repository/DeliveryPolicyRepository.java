package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryPolicyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicyEntity, String> {
    List<DeliveryPolicyEntity> findByTenantIdAndActiveTrue(UUID tenantId);
    Optional<DeliveryPolicyEntity> findByTenantIdAndDeliveryTypeAndActiveTrue(UUID tenantId, String deliveryType);
}
