package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DeliveryRequestEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequestEntity, UUID> {
    List<DeliveryRequestEntity> findTop100ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
