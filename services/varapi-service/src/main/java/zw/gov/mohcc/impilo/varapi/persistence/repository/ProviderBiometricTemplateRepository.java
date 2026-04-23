package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricModality;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderBiometricTemplateRepository extends JpaRepository<ProviderBiometricTemplateEntity, Long> {

    List<ProviderBiometricTemplateEntity> findByTenantIdAndProviderIdAndStatus(
            UUID tenantId, Long providerId, String status);

    Optional<ProviderBiometricTemplateEntity> findByTenantIdAndProviderIdAndModalityAndPositionAndStatus(
            UUID tenantId, Long providerId, ProviderBiometricModality modality, String position, String status);

    List<ProviderBiometricTemplateEntity> findByTenantIdAndModalityAndStatus(
            UUID tenantId, ProviderBiometricModality modality, String status);
}
