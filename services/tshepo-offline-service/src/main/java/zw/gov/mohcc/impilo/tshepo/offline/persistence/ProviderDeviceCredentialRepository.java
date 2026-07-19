package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderDeviceCredentialRepository
        extends JpaRepository<ProviderDeviceCredentialEntity, UUID> {

    Optional<ProviderDeviceCredentialEntity> findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
            UUID tenantId, String providerPublicId, String deviceId, String status);
}
