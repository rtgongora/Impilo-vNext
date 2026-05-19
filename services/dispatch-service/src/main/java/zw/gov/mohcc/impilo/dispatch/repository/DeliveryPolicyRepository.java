package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DeliveryPolicyEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicyEntity, UUID> {
    List<DeliveryPolicyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
