package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCertificateRepository extends JpaRepository<ProviderCertificateEntity, Long> {

    List<ProviderCertificateEntity> findByProviderId(Long providerId);

    List<ProviderCertificateEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    List<ProviderCertificateEntity> findByProviderIdAndStatus(Long providerId, String status);

    Optional<ProviderCertificateEntity> findByProviderIdAndCertificateTypeAndStatus(Long providerId, String certificateType, String status);

    List<ProviderCertificateEntity> findByExpiryDateBefore(LocalDate date);

    Optional<ProviderCertificateEntity> findByCertificateNumber(String certificateNumber);

    List<ProviderCertificateEntity> findByStatusAndExpiryDateBefore(String status, LocalDate date);
}