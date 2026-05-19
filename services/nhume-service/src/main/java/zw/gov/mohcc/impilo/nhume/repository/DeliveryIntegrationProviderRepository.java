package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryIntegrationProviderEntity;

import java.util.List;
import java.util.UUID;

public interface DeliveryIntegrationProviderRepository
        extends JpaRepository<DeliveryIntegrationProviderEntity, String> {
    List<DeliveryIntegrationProviderEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
