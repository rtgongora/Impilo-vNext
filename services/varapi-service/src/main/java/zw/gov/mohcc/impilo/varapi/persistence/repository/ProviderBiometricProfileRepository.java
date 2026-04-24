package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface ProviderBiometricProfileRepository extends JpaRepository<ProviderBiometricProfileEntity, UUID> {

    Optional<ProviderBiometricProfileEntity> findByTenantIdAndProviderId(UUID tenantId, Long providerId);
}
