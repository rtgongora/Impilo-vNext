package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DeliveryIntegrationProviderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryIntegrationProviderRepository extends JpaRepository<DeliveryIntegrationProviderEntity, UUID> {
    List<DeliveryIntegrationProviderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<DeliveryIntegrationProviderEntity> findByTenantIdAndProviderCode(UUID tenantId, String providerCode);
}
